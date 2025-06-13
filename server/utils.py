import os
import pandas as pd
import numpy as np
from PIL import Image
import threading

# Keep your global configs if needed (or import them from config.py)
from config import DATA_INFO_CSV_PATH, IMAGE_STORAGE_PATH
from data_utils import load_category_mapping, save_verified_label

# Load category mapping once
category_mapping = load_category_mapping()

def save_image(image_data, filename):
    """Saves received image to the storage directory."""
    image_path = os.path.join(IMAGE_STORAGE_PATH, filename)
    with open(image_path, "wb") as img_file:
        img_file.write(image_data)
    return image_path


def update_data_info(image_filename, predicted_label, predicted_bounding_box):
    """Safely updates data_info.csv with the predicted bounding box and label, and returns its img_id."""
    csv_lock = threading.Lock()
    with csv_lock:  # Prevent race conditions
        # Load existing CSV or create a new one
        if os.path.exists(DATA_INFO_CSV_PATH):
            df = pd.read_csv(DATA_INFO_CSV_PATH)
        else:
            df = pd.DataFrame(columns=["img_id", "img_path", "category", "category_id", "x1", "y1", "x2", "y2"])

        # Ensure bounding box is a valid list (convert NumPy array if necessary)
        if isinstance(predicted_bounding_box, np.ndarray):
            predicted_bounding_box = predicted_bounding_box.tolist()  # Convert NumPy array to list

        # Validate bounding box format
        if isinstance(predicted_bounding_box, (list, tuple)) and len(predicted_bounding_box) == 4:
            x1, y1, x2, y2 = predicted_bounding_box
        else:
            print(f"❌ Error: Invalid bounding box format for {image_filename}. Received: {predicted_bounding_box}")
            return None, None

        # Get category ID using load_category_mapping()
        category_mapping = load_category_mapping()
        category_id = category_mapping.get(predicted_label, -1)

        # If category is new, assign a new category_id
        if category_id == -1:
            category_id = max(category_mapping.values(), default=0) + 1
            category_mapping[predicted_label] = category_id
            print(f"🔄 Assigned new category_id {category_id} for '{predicted_label}'.")

        # Assign a new unique image ID (find the highest existing ID + 1)
        img_id = df["img_id"].max() + 1 if not df.empty else 1

        # Normalize path and ensure it uses forward slashes
        image_filename = os.path.normpath(image_filename).replace("\\", "/")

        new_entry = pd.DataFrame([[img_id, image_filename, predicted_label, category_id, x1, y1, x2, y2]],
                         columns=df.columns)        
        
        df = pd.concat([df, new_entry], ignore_index=True)
        print(f"✅ Stored new image '{image_filename}' with label '{predicted_label}' (img_id: {img_id}, category_id: {category_id}).")
           

        # Save back to CSV
        df.to_csv(DATA_INFO_CSV_PATH, index=False)
    
    return img_id