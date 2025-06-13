import os

# Model file path
MODEL_PATH = "food_detection_model.h5"
USE_UPDATED_MODEL = False

# UDP Server Configuration
UDP_IP = "0.0.0.0"
UDP_PORT = 5005

# Confidence threshold for predictions
CONFIDENCE_THRESHOLD = 0.01

# Retraining flag
SHOULD_TRAIN = False  # No retraining

DATA_INFO_CSV_PATH = "data_info.csv"

IMAGE_STORAGE_PATH = "UECFOOD100/verified_images"
os.makedirs(IMAGE_STORAGE_PATH, exist_ok=True)

