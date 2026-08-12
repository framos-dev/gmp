#!/usr/bin/env python3
import argparse
import json
import threading
from typing import Dict

import stomp

SUPPORTED_COMMANDS = {
    "INIT",
    "APPLY",
    "DATUM",
    "PARK",
    "VERIFY",
    "END_VERIFY",
    "GUIDE",
    "END_GUIDE",
    "PAUSE",
    "CONTINUE",
    "STOP",
    "STOP_CYCLE",
    "ABORT",
    "TEST",
}


class PrintListener(stomp.ConnectionListener):
    def __init__(
        self,
        stop_event: threading.Event,
        conn: stomp.Connection,
        response: str,
        error_message: str,
        reply_transformation: str,
    ) -> None:
        self._stop_event = stop_event
        self._conn = conn
        self._response = response
        self._error_message = error_message
        self._reply_transformation = reply_transformation

    def on_message(self, frame) -> None:
        print(f"received: {frame.body}")
        print(f"body_repr: {frame.body!r}")
        print(f"headers: {frame.headers}")
        self._print_decoded_body(frame)
        if self._is_supported_command(frame):
            self._handle_command(frame)

    def on_error(self, frame) -> None:
        print(f"error: {frame.body}")

    def on_disconnected(self) -> None:
        self._stop_event.set()

    def _is_supported_command(self, frame) -> bool:
        return self._command_name(frame) in SUPPORTED_COMMANDS

    def _print_decoded_body(self, frame) -> None:
        body = frame.body
        if isinstance(body, bytes):
            try:
                body = body.decode("utf-8")
            except UnicodeDecodeError:
                return
        if not isinstance(body, str):
            return
        text = body.strip()
        if not text:
            return
        try:
            decoded = json.loads(text)
        except json.JSONDecodeError:
            return
        print(f"decoded_body: {decoded}")

    def _command_name(self, frame) -> str:
        destination = (frame.headers or {}).get("destination", "")
        if destination.startswith("/topic/GMP.SC."):
            command_path = destination[len("/topic/GMP.SC.") :]
            return command_path.split(".", 1)[0]

        return ""

    def _handle_command(self, frame) -> None:
        headers = frame.headers or {}
        reply_to = headers.get("reply-to") or headers.get("JMSReplyTo")
        correlation_id = headers.get("correlation-id") or headers.get("JMSCorrelationID")
        command_name = self._command_name(frame)
        print(f"command_name: {command_name}")

        if not reply_to:
            print(f"missing reply-to header; cannot send {command_name} response")
            return

        reply_body = {
            "GMP_HANDLER_RESPONSE": self._response,
        }
        if self._response == "ERROR":
            reply_body["GMP_HANDLER_RESPONSE_ERROR"] = self._error_message

        send_headers = {
            "content-type": "application/json",
            "transformation": self._reply_transformation,
        }
        if correlation_id:
            send_headers["correlation-id"] = correlation_id

        body = encode_xstream_map_json(reply_body)
        self._conn.send(destination=reply_to, body=body, headers=send_headers)
        print(f"sent {command_name} response {self._response} to {reply_to}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Receive STOMP messages from ActiveMQ.")
    parser.add_argument("--host", default="127.0.0.1", help="Broker host")
    parser.add_argument("--port", type=int, default=61613, help="STOMP port")
    parser.add_argument("--user", default="", help="Username (optional)")
    parser.add_argument("--password", default="", help="Password (optional)")
    parser.add_argument(
        "--destination",
        default="/topic/GMP.SC.>",
        help="STOMP destination, e.g. /topic/GMP.SC.> or /topic/GMP.STATUS.gpi:status1",
    )
    parser.add_argument(
        "--transformation",
        default="jms-map-json",
        help="Optional ActiveMQ STOMP transformation, e.g. jms-map-json",
    )
    parser.add_argument(
        "--reply-transformation",
        default="jms-map-json",
        help="ActiveMQ STOMP transformation for handler replies",
    )
    parser.add_argument(
        "--response",
        default="COMPLETED",
        help="Handler response to send (COMPLETED, STARTED, ACCEPTED, NOANSWER, ERROR)",
    )
    parser.add_argument(
        "--error-message",
        default="",
        help="Error message to send when --response=ERROR",
    )
    return parser


def main() -> None:
    args = build_parser().parse_args()
    args.response = args.response.upper()
    stop_event = threading.Event()
    # ActiveMQ 5.6 bypasses STOMP transformations when content-length is set.
    conn = stomp.Connection([(args.host, args.port)], auto_content_length=False)
    conn.set_listener(
        "",
        PrintListener(
            stop_event,
            conn,
            args.response,
            args.error_message,
            args.reply_transformation,
        ),
    )
    conn.connect(args.user, args.password, wait=True)
    subscribe_headers = {}
    if args.transformation:
        subscribe_headers["transformation"] = args.transformation
    conn.subscribe(destination=args.destination, id=1, ack="auto", headers=subscribe_headers)

    print(f"listening on {args.destination} (ctrl-c to quit)")
    try:
        stop_event.wait()
    except KeyboardInterrupt:
        pass
    finally:
        conn.disconnect()


def encode_xstream_map_json(values: Dict[str, str]) -> str:
    entries = [{"string": [key, value]} for key, value in values.items()]
    entry_value = entries[0] if len(entries) == 1 else entries
    return json.dumps(
        {"linked-hash-map": {"entry": entry_value}},
        separators=(",", ":"),
    )


if __name__ == "__main__":
    main()
