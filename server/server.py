from udp_handler import start_udp_server
from mqtt_handler import client
import threading

if __name__ == "__main__":
    # Start UDP server in a separate thread
    threading.Thread(target=start_udp_server, daemon=True).start()
    # Start MQTT loop in main thread
    client.loop_forever()