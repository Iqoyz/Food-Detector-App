import paho.mqtt.client as mqtt
import threading
import json
import socket  
import math

MQTT_BROKER = "broker.hivemq.com"
MQTT_PORT = 1883
TOPIC_IMAGE = "project/images"
TOPIC_PREDICTIONS = "project/predictions"
TOPIC_CONFIRMED_LABELS = "project/confirmed_labels"

client = mqtt.Client()

def on_connect(client, userdata, flags, rc):
    print("Connected to MQTT broker with result code", rc)
    client.subscribe(TOPIC_IMAGE)
    client.subscribe(TOPIC_CONFIRMED_LABELS)

def on_message(client, userdata, msg):
    # run handle_client in thread
    threading.Thread(target=handle_client, args=(msg.payload, msg.topic)).start()

def send_udp_in_chunks(udp_sock, data, addr, chunk_size=60000):
    total_packets = math.ceil(len(data) / chunk_size)
    # print(f"📦 Sending {total_packets} UDP packets...")

    for i in range(0, len(data), chunk_size):
        chunk = data[i:i+chunk_size]
        udp_sock.sendto(chunk, addr)
        # print(f"✅ Sent packet {i // chunk_size + 1}/{total_packets}")

    udp_sock.sendto(b"END", addr)
    print("🏁 Sent END signal")


# UDP CONFIG — adjust to match udp_handler.py
UDP_IP = "127.0.0.1"  # same machine
UDP_PORT = 5005       # match udp_handler.py port


import base64

def handle_client(data, topic):
    client_id = "unknown"
    image_bytes = None

    try:
        received_text = data.decode("utf-8")
        message_json = json.loads(received_text)

        client_id = message_json.get("client_id", "unknown")

        udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        udp_sock.settimeout(10)

        if topic == TOPIC_IMAGE:
            image_base64 = message_json.get("image_data")
            if image_base64:
                image_bytes = base64.b64decode(image_base64)
                # Send the image in safe chunks
                send_udp_in_chunks(udp_sock, image_bytes, (UDP_IP, UDP_PORT))

                response_data, _ = udp_sock.recvfrom(65536)
                prediction_result = response_data.decode("utf-8")

                response_payload = json.dumps({
                    "client_id": client_id,
                    "prediction": prediction_result
                })

                client.publish(TOPIC_PREDICTIONS, response_payload)

            else:
                print("⚠ No image_data found in payload")
                return  # skip if no image

        elif topic == TOPIC_CONFIRMED_LABELS:
            img_id = message_json.get("img_id")
            confirmed_labels = message_json.get("confirmed_labels", [])

            print(f"📩 Received verified labels for img_id {img_id}: {confirmed_labels}")

            if img_id and confirmed_labels:
                print(f"✅ Forwarding confirmed labels to UDP for saving and retraining...")
                payload_to_send = json.dumps({
                    "img_id": img_id,
                    "confirmed_labels": confirmed_labels
                }).encode("utf-8")

                udp_sock.sendto(payload_to_send, (UDP_IP, UDP_PORT))
        else:
            print(f"⚠ Unknown topic {topic}")
            return

        udp_sock.close()

    except Exception as e:
        error_payload = json.dumps({
            "client_id": client_id,
            "error": f"UDP error: {str(e)}"
        })
        client.publish(TOPIC_PREDICTIONS, error_payload)



client.on_connect = on_connect
client.on_message = on_message

client.connect(MQTT_BROKER, MQTT_PORT, 60)

