package com.example.examplebluetooth;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class TensorFlowModelRunner {
    private Interpreter interpreter;
    private int inputSize;
    private int outputSize;
    private ByteBuffer inputBuffer;
    private ByteBuffer outputBuffer;

    public TensorFlowModelRunner(AssetManager assetManager, String modelPath, int inputSize, int outputSize) throws IOException {
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        MappedByteBuffer modelBuffer = loadModelFile(assetManager, modelPath);
        interpreter = new Interpreter(modelBuffer);

        // Allocate input and output buffers
        inputBuffer = ByteBuffer.allocateDirect(inputSize * Float.BYTES);
        outputBuffer = ByteBuffer.allocateDirect(outputSize * Float.BYTES);
        inputBuffer.order(ByteOrder.nativeOrder());
        outputBuffer.order(ByteOrder.nativeOrder());
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath)throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);

    }

    public float[] runInference(float[] inputData) {
        inputBuffer.rewind();
        inputBuffer.asFloatBuffer().put(inputData);
        interpreter.run(inputBuffer, outputBuffer);
        outputBuffer.rewind();
        float[] outputData = new float[outputSize];
        outputBuffer.asFloatBuffer().get(outputData);
        return outputData;
    }
    public String toString(){
        return "Tensorflow Model GO BRRRR";
    }
}
