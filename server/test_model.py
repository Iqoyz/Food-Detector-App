import numpy as np
from PIL import Image
from model_utils import predict
from data_utils import load_category_mapping
import tensorflow as tf
from tensorflow.keras.models import load_model
from config import MODEL_PATH

# Load model
custom_objects = {"mse": tf.keras.losses.MeanSquaredError()}
model = load_model(MODEL_PATH, custom_objects=custom_objects)

# Load category mapping
category_mapping = load_category_mapping()

# Load test image
image_path = "rice.jpg" #replace with your image source  
image = Image.open(image_path)

# Run prediction (pass the model)
result = predict(image, category_mapping)

# Print output
print("🔍 Prediction Results:")
for prediction in result["predictions"]:
    print(f"🍽️ {prediction['label']} ({prediction['confidence']:.2%}) - Bounding Box: {prediction['bounding_box']}")
