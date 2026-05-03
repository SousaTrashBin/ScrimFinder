import json
import os
import threading
import time

import pika

from training_service.grpc_server import process_match_for_training

RABBITMQ_HOST = os.environ.get("RABBITMQ_HOST", "rabbitmq")
RABBITMQ_PORT = int(os.environ.get("RABBITMQ_PORT", 5672))
RABBITMQ_USER = os.environ.get("RABBITMQ_USER", "guest")
RABBITMQ_PASSWORD = os.environ.get("RABBITMQ_PASSWORD", "guest")
TRAINING_EXCHANGE = os.environ.get("TRAINING_EXCHANGE", "scrim.training.forward-match")
TRAINING_QUEUE = os.environ.get("TRAINING_QUEUE", "scrim.training.forward-match.q")
TRAINING_ROUTING_KEY = os.environ.get("TRAINING_ROUTING_KEY", "training.forward-match")

_stop_event = threading.Event()
_thread = None


def _consume_loop():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASSWORD)
    params = pika.ConnectionParameters(
        host=RABBITMQ_HOST,
        port=RABBITMQ_PORT,
        credentials=credentials,
        heartbeat=30,
        blocked_connection_timeout=120,
    )

    while not _stop_event.is_set():
        connection = None
        try:
            connection = pika.BlockingConnection(params)
            channel = connection.channel()
            channel.exchange_declare(
                exchange=TRAINING_EXCHANGE, exchange_type="direct", durable=True
            )
            channel.queue_declare(queue=TRAINING_QUEUE, durable=True)
            channel.queue_bind(
                queue=TRAINING_QUEUE,
                exchange=TRAINING_EXCHANGE,
                routing_key=TRAINING_ROUTING_KEY,
            )
            channel.basic_qos(prefetch_count=10)

            def on_message(ch, method, properties, body):
                try:
                    payload = json.loads(body.decode("utf-8"))
                    match_id = payload.get("matchId") or payload.get("match_id")
                    source = payload.get("source", "history")
                    if not match_id:
                        raise ValueError("Missing matchId/match_id")
                    process_match_for_training(match_id=match_id, source=source)
                    ch.basic_ack(delivery_tag=method.delivery_tag)
                except Exception as exc:
                    print(f"[rabbitmq] Failed to process message: {exc}", flush=True)
                    ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)

            channel.basic_consume(queue=TRAINING_QUEUE, on_message_callback=on_message)
            print(
                f"[rabbitmq] Consuming training events from {TRAINING_QUEUE} via {TRAINING_EXCHANGE}",
                flush=True,
            )

            while not _stop_event.is_set():
                connection.process_data_events(time_limit=1)
        except Exception as exc:
            print(f"[rabbitmq] Consumer error: {exc}. Retrying in 5s...", flush=True)
            time.sleep(5)
        finally:
            if connection and connection.is_open:
                connection.close()


def start_background_consumer():
    global _thread
    if _thread is not None and _thread.is_alive():
        return
    _stop_event.clear()
    _thread = threading.Thread(
        target=_consume_loop, name="rabbitmq-consumer-thread", daemon=True
    )
    _thread.start()


def stop_consumer():
    _stop_event.set()
