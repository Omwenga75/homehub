import tensorflow as tf
from tensorflow import keras
import numpy as np
import os
import json
from pathlib import Path
import matplotlib.pyplot as plt
import cv2
from sklearn.model_selection import train_test_split

class DocumentVerificationModel:
    """
    A comprehensive document verification model that classifies Kenyan identification documents
    including National IDs, Passports, Student IDs, and Business Permits.
    """
    
    def __init__(self, data_path="training_data", model_path="models"):
        self.data_path = Path(data_path)
        self.model_path = Path(model_path)
        self.model_path.mkdir(exist_ok=True)
        
        self.class_names = [
            'NATIONAL_ID',
            'PASSPORT',
            'STUDENT_ID',
            'BUSINESS_PERMIT',
            'UNKNOWN'
        ]
        
        self.input_size = (224, 224)
        self.num_classes = len(self.class_names)
        self.batch_size = 32
        self.epochs = 50
        
        self.base_model = None
        self.model = None
        self.history = None
        
    def setup_data_generators(self):
        train_datagen = tf.keras.preprocessing.image.ImageDataGenerator(
            rescale=1./255,
            rotation_range=20,
            width_shift_range=0.2,
            height_shift_range=0.2,
            shear_range=0.2,
            zoom_range=0.2,
            horizontal_flip=True,
            fill_mode='nearest',
            brightness_range=[0.8, 1.2],
            channel_shift_range=20.0
        )
        
        val_datagen = tf.keras.preprocessing.image.ImageDataGenerator(
            rescale=1./255
        )
        
        train_generator = train_datagen.flow_from_directory(
            self.data_path / 'train',
            target_size=self.input_size,
            batch_size=self.batch_size,
            class_mode='categorical',
            classes=self.class_names,
            shuffle=True,
            seed=42
        )
        
        val_generator = val_datagen.flow_from_directory(
            self.data_path / 'val',
            target_size=self.input_size,
            batch_size=self.batch_size,
            class_mode='categorical',
            classes=self.class_names,
            shuffle=False
        )
        
        return train_generator, val_generator
    
    def build_model(self):
        self.base_model = tf.keras.applications.MobileNetV2(
            input_shape=(self.input_size[0], self.input_size[1], 3),
            include_top=False,
            weights='imagenet'
        )
        self.base_model.trainable = False
        
        self.model = keras.Sequential([
            self.base_model,
            keras.layers.GlobalAveragePooling2D(),
            keras.layers.Dense(128, activation='relu'),
            keras.layers.Dropout(0.5),
            keras.layers.Dense(self.num_classes, activation='softmax')
        ])
        
        self.model.compile(
            optimizer=keras.optimizers.Adam(learning_rate=0.001),
            loss='categorical_crossentropy',
            metrics=['accuracy']
        )
        
        return self.model
    
    def setup_callbacks(self):
        early_stopping = keras.callbacks.EarlyStopping(
            monitor='val_accuracy',
            patience=10,
            restore_best_weights=True,
            verbose=1
        )
        
        reduce_lr = keras.callbacks.ReduceLROnPlateau(
            monitor='val_loss',
            factor=0.5,
            patience=5,
            min_lr=1e-7,
            verbose=1
        )
        
        model_checkpoint = keras.callbacks.ModelCheckpoint(
            self.model_path / 'best_document_verification_model.h5',
            monitor='val_accuracy',
            save_best_only=True,
            verbose=1
        )
        
        return [early_stopping, reduce_lr, model_checkpoint]
    
    def train(self):
        train_generator, val_generator = self.setup_data_generators()
        self.build_model()
        callbacks = self.setup_callbacks()
        
        self.history = self.model.fit(
            train_generator,
            steps_per_epoch=max(1, train_generator.samples // self.batch_size),
            epochs=self.epochs,
            validation_data=val_generator,
            validation_steps=max(1, val_generator.samples // self.batch_size),
            callbacks=callbacks,
            verbose=1
        )
        
        self.model.save(self.model_path / 'document_verification_model_final.h5')
        return self.history
    
    def export_to_tflite(self, quantize=True):
        best_model_path = self.model_path / 'best_document_verification_model.h5'
        if best_model_path.exists():
            model = keras.models.load_model(best_model_path)
        else:
            model = self.model
        
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        
        if quantize:
            converter.optimizations = [tf.lite.Optimize.DEFAULT]
            def representative_dataset():
                val_datagen = tf.keras.preprocessing.image.ImageDataGenerator(rescale=1./255)
                val_gen = val_datagen.flow_from_directory(
                    self.data_path / 'val',
                    target_size=self.input_size,
                    batch_size=1,
                    class_mode='categorical',
                    shuffle=False
                )
                for i in range(min(100, val_gen.samples)):
                    yield [val_gen[i][0].astype(np.float32)]
            
            converter.representative_dataset = representative_dataset
            converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
            converter.inference_input_type = tf.uint8
            converter.inference_output_type = tf.uint8
        
        tflite_model = converter.convert()
        tflite_path = self.model_path / 'document_verification_model_quantized.tflite'
        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)
            
        labels_path = self.model_path / 'labels.txt'
        with open(labels_path, 'w') as f:
            for label in self.class_names:
                f.write(f"{label}\n")
        
        return tflite_path

if __name__ == "__main__":
    trainer = DocumentVerificationModel(data_path="training_data", model_path="models")
    trainer.train()
    trainer.export_to_tflite(quantize=True)
