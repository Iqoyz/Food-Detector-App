import os
import pandas as pd
import threading
from config import DATA_INFO_CSV_PATH

# Lock for safe CSV access
csv_lock = threading.Lock()


def load_category_mapping():
    """Loads category mappings from CSV."""
    data_info = pd.read_csv("data_info.csv")
    data_info['category_id'] = data_info['category_id'].astype(int)
    return dict(zip(data_info['category'], data_info['category_id']))

def save_verified_label(img_id, confirmed_label, bounding_box):
    """
    Updates the user-verified label in data_info.csv using img_id.
    Ensures category_id is updated and prevents duplicate entries.
    """
    with csv_lock:  # Prevent race conditions
        # Load existing CSV
        if os.path.exists(DATA_INFO_CSV_PATH):
            df = pd.read_csv(DATA_INFO_CSV_PATH)
        else:
            print(f"❌ Error: data_info.csv not found.")
            return False

        # Check if img_id exists in CSV
        if img_id not in df["img_id"].values:
            print(f"❌ Error: img_id {img_id} not found in data_info.csv.")
            return False

        # Load category mapping to find category_id
        category_mapping = load_category_mapping()

        # Ensure confirmed_label is a string
        if not isinstance(confirmed_label, str):
            print(f"⚠ Warning: Expected string but got {type(confirmed_label)}. Skipping update.")
            return False

        category_id = category_mapping.get(confirmed_label, -1)

        # If the label is new, assign a new category_id
        if category_id == -1:
            category_id = max(category_mapping.values(), default=0) + 1
            category_mapping[confirmed_label] = category_id
            print(f"🔄 Assigned new category_id {category_id} for '{confirmed_label}'.")

        # Ensure bounding box values are valid
        if isinstance(bounding_box, list) and len(bounding_box) == 4:
            x1, y1, x2, y2 = bounding_box
        else:
            print(f"❌ Error: Invalid bounding box format for img_id {img_id}. Received: {bounding_box}")
            return False

        # Update category, category_id, and bounding box for the given img_id
        df.loc[df["img_id"] == img_id, ["category", "category_id", "x1", "y1", "x2", "y2"]] = [
            confirmed_label, category_id, x1, y1, x2, y2
        ]
        print(f"🔄 Updated verified label for img_id {img_id} with '{confirmed_label}'.")

        # Save back to CSV
        df.to_csv(DATA_INFO_CSV_PATH, index=False)

    return True
