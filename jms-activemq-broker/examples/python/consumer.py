#!/usr/bin/env python3
import argparse
import json
import threading

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
    ) -> None:
        self._stop_event = stop_event
        self._conn = conn
        self._response = response
        self._error_message = error_message

    def on_message(self, frame) -> None:
        print(f"received: {frame.body}")
        print(f"body_type: {type(frame.body).__name__}")
        print(f"body_repr: {frame.body!r}")
        print(f"headers: {frame.headers}")
        self._print_body_diagnostics(frame)
        self._print_decoded_body(frame)
        if self._is_supported_command(frame):
            self._handle_command(frame)

    def on_error(self, frame) -> None:
        print(f"error: {frame.body}")

    def on_disconnected(self) -> None:
        self._stop_event.set()

    def _is_supported_command(self, frame) -> bool:
        return self._command_name(frame) in SUPPORTED_COMMANDS

    def _print_body_diagnostics(self, frame) -> None:
        body = frame.body
        if isinstance(body, str):
            raw = body.encode("latin-1", errors="replace")
        elif isinstance(body, bytes):
            raw = body
        else:
            print("body_diagnostics: unsupported body type")
            return

        preview = raw[:64]
        print(f"body_len: {len(raw)}")
        print(f"body_hex_preview: {preview.hex()}")

        latin1_preview = preview.decode("latin-1", errors="replace")
        print(f"body_latin1_preview: {latin1_preview!r}")

        try:
            utf8_preview = preview.decode("utf-8")
            print(f"body_utf8_preview: {utf8_preview!r}")
        except UnicodeDecodeError:
            print("body_utf8_preview: <non-utf8>")

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
        print(f'command_name: {command_name}')

        if not reply_to:
            print(f"missing reply-to header; cannot send {command_name} response")
            return

        send_headers = {
            "content-type": "text/plain",
            "GMP_HANDLER_RESPONSE": self._response,
        }
        if self._response == "ERROR":
            send_headers["GMP_HANDLER_RESPONSE_ERROR"] = self._error_message
        if correlation_id:
            send_headers["correlation-id"] = correlation_id

        self._conn.send(destination=reply_to, body="", headers=send_headers)
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
    conn = stomp.Connection([(args.host, args.port)])
    conn.set_listener(
        "",
        PrintListener(stop_event, conn, args.response, args.error_message),
    )
    conn.connect(args.user, args.password, wait=True)
    conn.subscribe(destination=args.destination, id=1, ack="auto")

    print(f"listening on {args.destination} (ctrl-c to quit)")
    try:
        stop_event.wait()
    except KeyboardInterrupt:
        pass
    finally:
        conn.disconnect()


if __name__ == "__main__":
    main()
