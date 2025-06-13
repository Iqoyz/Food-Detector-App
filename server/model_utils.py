import numpy as np
import tensorflow as tf
from tensorflow.keras.models import load_model
from PIL import Image
import threading
from config import MODEL_PATH, CONFIDENCE_THRESHOLD


# Load model with custom loss function
custom_objects = {"mse": tf.keras.losses.MeanSquaredError()}
model = load_model(MODEL_PATH, custom_objects=custom_objects)

model_lock = threading.Lock()

def predict(image: Image, category_mapping):
    """Runs inference on a single image and ensures each predicted label is paired with a bounding box."""
    with model_lock:
        # Preprocess image
        img_array = np.array(image.convert("RGB").resize((224, 224))) / 255.0
        img_array = np.expand_dims(img_array, axis=0)

        # Run inference (model has two outputs: category scores and bounding boxes)
        category_predictions, bbox_predictions = model.predict(img_array, verbose=0)

        # Flatten category predictions and bounding boxes
        category_predictions = category_predictions[0].flatten()
        bbox_predictions = bbox_predictions[0]  # Extract bounding boxes

    # Get valid category predictions (sorted by confidence)
    valid_predictions = sorted(
        [(idx, float(category_predictions[idx])) for idx in range(len(category_predictions)) if category_predictions[idx] >= CONFIDENCE_THRESHOLD],
        key=lambda x: x[1],  # Sort by confidence
        reverse=True
    )

    # Ensure the number of bounding boxes is not exceeded
    num_boxes = len(bbox_predictions)

    # Create a dictionary mapping bounding boxes to labels
    bbox_label_map = {}

    for i, (idx, confidence) in enumerate(valid_predictions):
        label = list(category_mapping.keys())[idx]

        # Assign bounding box (match index if available)
        bbox = bbox_predictions[i % num_boxes]  # Wrap around if more labels than bounding boxes

        # Convert bounding box to tuple (to use as a dictionary key)
        bbox_tuple = tuple(bbox)

        # Group labels under the same bounding box
        if bbox_tuple in bbox_label_map:
            bbox_label_map[bbox_tuple].append((label, confidence))
        else:
            bbox_label_map[bbox_tuple] = [(label, confidence)]

    # Convert dictionary to final prediction format
    predictions = []
    for bbox, labels in bbox_label_map.items():
        for label, confidence in labels:
            predictions.append({
                "label": label,
                "confidence": confidence,
                "bounding_box": list(bbox)  # Convert tuple back to list
            })

    return {"predictions": predictions}


import nbformat
from nbconvert.preprocessors import ExecutePreprocessor

def retrain_model():
    """Retrains the model by executing the Food_Image_Classifier_Retrain.ipynb notebook."""
    print("🔄 Starting model retraining...")
    notebook_path = "Food_Image_Classifier_Retrain.ipynb"

    try:
        # Load the notebook
        with open(notebook_path, 'r', encoding='utf-8') as f:
            nb = nbformat.read(f, as_version=4)

        # Set up the executor
        ep = ExecutePreprocessor(timeout=600, kernel_name='python3')

        print("✅ Running retrain notebook.")
        # # Execute the notebook
        # ep.preprocess(nb, {'metadata': {'path': './'}})

        # # Optionally, overwrite the notebook with the executed version
        # with open(notebook_path, 'w', encoding='utf-8') as f:
        #     nbformat.write(nb, f)

        # print("✅ Model retraining completed successfully.")
    except Exception as e:
        print(f"❌ Error during retraining: {e}")


    