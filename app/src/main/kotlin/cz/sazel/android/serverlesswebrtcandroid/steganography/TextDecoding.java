package cz.sazel.android.serverlesswebrtcandroid.steganography;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import java.util.Iterator;
import java.util.List;

import cz.sazel.android.serverlesswebrtcandroid.steganography.AsyncTaskCallback.TextDecodingCallback;
import cz.sazel.android.serverlesswebrtcandroid.steganography.Utils.Utility;
import cz.sazel.android.serverlesswebrtcandroid.steganography.Utils.Zipping;

public class TextDecoding extends AsyncTask<ImageSteganography, Void, ImageSteganography> {
    private static final String TAG = TextDecoding.class.getName();
    ImageSteganography result;
    TextDecodingCallback textDecodingCallback;

    public TextDecoding(TextDecodingCallback textDecodingCallback) {
        this.textDecodingCallback = textDecodingCallback;
    }

    protected void onPostExecute(ImageSteganography imageSteganography) {
        super.onPostExecute(imageSteganography);
        this.textDecodingCallback.onCompleteTextEncoding(this.result);
    }

    protected ImageSteganography doInBackground(ImageSteganography... imageSteganographies) {
        this.result = new ImageSteganography();
        if (imageSteganographies.length > 0) {
            ImageSteganography imageSteganography = imageSteganographies[0];
            Bitmap bitmap = imageSteganography.getImage();
            if (bitmap == null) {
                return null;
            }

            List<Bitmap> srcEncodedList = Utility.splitImage(bitmap);
            String decoded_message = EncodeDecode.decodeMessage(srcEncodedList);
            Log.d(TAG, "Decoded_Message : " + decoded_message);
            if (decoded_message != null) {
                this.result.setDecoded(true);
            }

            String decrypted_message = ImageSteganography.decryptMessage(decoded_message, imageSteganography.getSecret_key());
            Log.d(TAG, "Decrypted message : " + decrypted_message);
            String decompressed_message = null;
            if (decrypted_message != null) {
                this.result.setSecretKeyWrong(false);

                try {
                    decompressed_message = Zipping.decompress(decrypted_message.getBytes("ISO-8859-1"));
                    Log.d(TAG, "Original Message : " + decompressed_message);
                } catch (Exception var11) {
                    var11.printStackTrace();
                }

                if (!Utility.isStringEmpty(decompressed_message)) {
                    try {
                        if (this.result != null && this.result.isDecoded()) {
                            this.result.setMessage(decompressed_message);
                        }
                    } catch (Exception var10) {
                        var10.printStackTrace();
                    }
                }

                Iterator var8 = srcEncodedList.iterator();

                while (var8.hasNext()) {
                    Bitmap bitm = (Bitmap) var8.next();
                    bitm.recycle();
                }

                System.gc();
            }
        }

        return this.result;
    }
}