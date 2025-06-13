# 🍱 Food Image Classifier

This project is a deep learning model for classifying and localizing Japanese food items using images from the UEC FOOD 100 dataset.

---

## 📚 Dataset

- **Dataset:** [UEC FOOD 100](http://foodcam.mobi/dataset100.html)  
- **Description:** 100 Japanese food categories with approximately 14,000 labeled images.  
- **Format:** JPG images with bounding box annotations for each food item.

---

## ⚙️ Method of Training

I used **MobileNetV2** as the base model because it is a lightweight convolutional neural network (CNN) architecture that is highly efficient and well-suited for mobile and embedded vision applications. MobileNetV2 is pretrained on the ImageNet dataset, which provides excellent general feature extraction capabilities — making it an ideal backbone for fine-tuning on specialized tasks like food classification.

### 🛠️ Summary of My Training Process

1. **Base model loading & freezing**  
   - Loaded MobileNetV2 (`include_top=False`) to remove the original classification head.  
   - Set `trainable = False` to freeze its layers, so it acted as a fixed feature extractor without updating pretrained weights.

2. **Custom head design**  
   - Added:
     - `GlobalAveragePooling2D` layer to reduce spatial dimensions.
     - Dense layer with ReLU activation for feature learning.
     - Two parallel outputs:
       - `category_output`: multi-label food category classification (sigmoid activation).
       - `bbox_output`: prediction of multiple bounding boxes (linear activation + reshape).

3. **Initial training (frozen base)**  
   - Compiled the model with:
     - **Loss:** binary crossentropy (category), mean squared error (bounding box).
     - **Optimizer:** Adam (`learning_rate=0.0005`).
     - **Metrics:** accuracy (category), mean absolute error (bounding box).
   - Trained for 7 epochs, focusing only on the new head layers.

4. **Fine-tuning (unfrozen base)**  
   - Unfroze MobileNetV2 (`base_model.trainable = True`) to allow full model training.
   - Recompiled with:
     - **Optimizer:** Adam (`learning_rate=0.0001`) to avoid catastrophic forgetting.
     - **Callbacks:** 
       - `EarlyStopping` (to stop when validation loss stopped improving).
       - `ReduceLROnPlateau` (to reduce the learning rate if validation loss plateaued).
   - Trained for 15 additional epochs.

5. **Final save**  
   - Saved the fully trained model as `food_detection_model.h5` for deployment or further evaluation.

---

## 📊 Training Results

After fine-tuning, I evaluated the model on the test dataset.

**Performance Summary:**
- ✅ Prediction Accuracy (category classification): **65.44%**
- 📏 Bounding Box Mean Absolute Error (MAE): **0.1772**
- 📉 Bounding Box Loss (MSE): **0.0812**
- 📉 Category Output Loss: **0.0267**
- 📉 Overall Combined Loss: **0.0616**

---

## 💬 Notes

- This project focuses on **both** classification and localization, which makes the task more challenging compared to pure classification models.
- The dataset is moderately small, so fine-tuning on a pretrained network like MobileNetV2 was essential to achieve reasonable performance.
- For further improvements, techniques like data augmentation, more advanced bounding box heads (e.g., using YOLO or EfficientDet), or additional training epochs can be explored.

---

## 📂 Files

- `food_detection_model.h5` → Final trained model.
- Training notebook → See `Food_Image_Classifier.ipynb` for full code and experiments.

---

