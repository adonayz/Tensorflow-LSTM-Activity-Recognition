package edu.wpi.adonay.cs4518_final;

public interface OnInferenceCompleted {
    void onInferenceCompleted(float[] probabilities, String activity, float probability, long elapsedTime);
}
