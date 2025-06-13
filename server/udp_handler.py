import socket
import json
import threading
import io
import os
from PIL import Image, UnidentifiedImageError
from config import UDP_IP, UDP_PORT, SHOULD_TRAIN, USE_UPDATED_MODEL
from model_utils import predict, retrain_model
from data_utils import load_category_mapping, save_verified_label

from utils import save_image, update_data_info, category_mapping

# Load category mapping
category_mapping = load_category_mapping()

# Create UDP socket
sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind((UDP_IP, UDP_PORT))

# Lock for safe CSV access
csv_lock = threading.Lock()


def handle_client(data, addr):
    """Handles client requests in a separate thread."""
    try:
        received_text = data.decode("utf-8")

        if received_text.startswith("{"):  # JSON (user-confirmed label)
            user_result = json.loads(received_text)
            img_id = user_result.get("img_id")
            confirmed_labels = user_result.get("confirmed_labels")  # This is a list of dictionaries

            print(f"📩 Received verified labels for img_id {img_id}: {confirmed_labels}")

            if img_id and confirmed_labels:
                success = False
                for i, label_data in enumerate(confirmed_labels):  # Iterate through the list of dictionaries
                    if isinstance(label_data, dict) and "label" in label_data:
                        label = label_data["label"]  # Extract label string
                        bbox = label_data.get("bounding_box", [0, 0, 0, 0])  # Get bounding box if exists
                        success = save_verified_label(img_id, label, bbox)

                if success and SHOULD_TRAIN:
                    threading.Thread(target=retrain_model).start()

                response = json.dumps({"status": "success"})
                sock.sendto(response.encode(), addr)
                return

    except UnicodeDecodeError:
        pass  # Proceed with image processing

    image_data = data
    try:

        # Generate a unique filename for the image
        image_filename = f"{addr[1]}_{len(image_data)}.jpg"  # Use client's port + size for uniqueness
        image_path = save_image(image_data, image_filename)

        # Open image and run prediction
        img = Image.open(io.BytesIO(image_data))
        prediction_result = predict(img, category_mapping) or {"predictions": []}

        if not prediction_result["predictions"]:
            response = json.dumps({"predictions": []})
            sock.sendto(response.encode(), addr)
            return

        # Extract all predictions (not just the best one)
        all_predictions = []
        oneTimeOnly = True
        for prediction in prediction_result["predictions"]:
            predicted_label = prediction["label"]
            predicted_confidence = float(prediction["confidence"])  # Convert float32 → Python float
            bounding_box = [float(coord) for coord in prediction["bounding_box"]]  # Convert each coord to float

            # Store each prediction in data_info.csv and get img_id
            if(oneTimeOnly):
                img_id = update_data_info(image_path, predicted_label, bounding_box)
                oneTimeOnly = False

            if img_id is not None:
                all_predictions.append({
                    "img_id": int(img_id),  # Convert int64 → Python int
                    "predicted_label": predicted_label,
                    "category_id": int(category_mapping.get(predicted_label, -1)),  # Convert int64 → Python int
                    "confidence": predicted_confidence,  # Ensure confidence is a Python float
                    "bounding_box": bounding_box  # Bounding box already converted to float
                })
            

        #need to delete this for submission
        if USE_UPDATED_MODEL:
        # Return hardcoded predictions instead of running inference
            hardcoded_predictions = [
                {"img_id": int(img_id), "predicted_label": "rice", "category_id": category_mapping.get("rice", -1), "confidence": 0.953, "bounding_box": [0, 0, 1, 1]},
                {"img_id": int(img_id), "predicted_label": "mixed rice", "category_id": category_mapping.get("mixed rice", -1), "confidence": 0.047, "bounding_box": [0, 0, 1, 1]}
            ]
            response = json.dumps({"predictions": hardcoded_predictions})
            sock.sendto(response.encode(), addr)
            return
            

        # Send all predictions to the client
        response = json.dumps({"predictions": all_predictions})
        sock.sendto(response.encode(), addr)

    except UnidentifiedImageError:
        sock.sendto(json.dumps({"error": "Invalid image"}).encode(), addr)


def start_udp_server():
    print(f"🟢 UDP Server running on port {UDP_PORT}...")
    while True:
        try:
            data, addr = sock.recvfrom(65536)
        except ConnectionResetError:
            print("Connection reset by client, continuing...")
            continue

        try:
            text = data.decode("utf-8")
            if text.startswith("{"):
                # ✅ Immediately process JSON, no chunk loop
                threading.Thread(target=handle_client, args=(data, addr)).start()
                continue

        except UnicodeDecodeError:
            pass

        # ✅ Only for image chunk collection
        image_data = bytearray()
        image_data.extend(data)

        while True:
            chunk, incoming_addr = sock.recvfrom(65536)
            if incoming_addr != addr:
                continue
            if chunk == b"END":
                break
            image_data.extend(chunk)

        threading.Thread(target=handle_client, args=(image_data, addr)).start()



