package cz.sazel.android.serverlesswebrtcandroid.steganography.AsyncTaskCallback;

import cz.sazel.android.serverlesswebrtcandroid.steganography.ImageSteganography;

public interface TextDecodingCallback {

    void onStartTextEncoding();

    void onCompleteTextEncoding(ImageSteganography result);

}