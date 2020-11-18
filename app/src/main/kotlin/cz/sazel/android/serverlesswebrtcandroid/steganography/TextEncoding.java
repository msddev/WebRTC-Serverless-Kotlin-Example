package cz.sazel.android.serverlesswebrtcandroid.steganography;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import java.util.Iterator;
import java.util.List;

import cz.sazel.android.serverlesswebrtcandroid.steganography.AsyncTaskCallback.TextEncodingCallback;
import cz.sazel.android.serverlesswebrtcandroid.steganography.Utils.Utility;

public class TextEncoding extends AsyncTask<ImageSteganography, Integer, ImageSteganography> {
    private static String TAG = TextEncoding.class.getName();
    ImageSteganography result;
    TextEncodingCallback callbackInterface;

    public TextEncoding(TextEncodingCallback callbackInterface) {
        this.callbackInterface = callbackInterface;
    }

    protected void onPostExecute(ImageSteganography textStegnography) {
        super.onPostExecute(textStegnography);
        this.callbackInterface.onCompleteTextEncoding(this.result);
    }

    protected ImageSteganography doInBackground(ImageSteganography... imageSteganographies) {
        this.result = new ImageSteganography();
        if (imageSteganographies.length > 0) {
            ImageSteganography textStegnography = imageSteganographies[0];
            Bitmap bitmap = textStegnography.getImage();
            int originalHeight = bitmap.getHeight();
            int originalWidth = bitmap.getWidth();
            List<Bitmap> src_list = Utility.splitImage(bitmap);
            List<Bitmap> encoded_list = EncodeDecode.encodeMessage(src_list, textStegnography.getEncrypted_message(), new EncodeDecode.ProgressHandler() {
                public void setTotal(int tot) {
                    Log.d(TAG, "Total Length : " + tot);
                }

                public void increment(int inc) {
                    publishProgress(new Integer[]{inc});
                }

                public void finished() {
                    Log.d(TAG, "Message Encoding end....");
                }
            });
            Iterator var9 = src_list.iterator();

            while (var9.hasNext()) {
                Bitmap bitm = (Bitmap) var9.next();
                bitm.recycle();
            }

            System.gc();
            Bitmap srcEncoded = Utility.mergeImage(encoded_list, originalHeight, originalWidth);
            this.result.setEncoded_image(srcEncoded);
            this.result.setEncoded(true);
        }

        return this.result;
    }
}