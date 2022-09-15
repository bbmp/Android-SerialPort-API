/*
 * Copyright 2009 Cedric Priscal
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */

package android.serialport.sample;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class LoopbackActivity extends SerialPortActivity {

    byte mValueToSend;

    byte[] payload = new byte[] {1, 2, 3, 4};
    boolean mByteReceivedBack;
    Object mByteReceivedBackSemaphore = new Object();
    Integer mIncoming = new Integer(0);
    Integer mOutgoing = new Integer(0);
    Integer mLost = new Integer(0);
    Integer mCorrupted = new Integer(0);

    SendingThread mSendingThread;
    TextView mTextViewOutgoing;
    TextView mTextViewIncoming;
    TextView mTextViewLost;
    TextView mTextViewCorrupted;
    private BlockingQueue blockingQueue = new ArrayBlockingQueue(1);

    private class SendingThread2 extends Thread {
        @Override
        public void run() {
            while (!isInterrupted()) {
                synchronized (mByteReceivedBackSemaphore) {
                    mByteReceivedBack = false;
                    try {
                        if (mOutputStream != null) {
                            LogUtils.e("take");
                            byte[] data = (byte[]) blockingQueue.take();
                            mOutputStream.write(data);
                            LogUtils.e("send" + new String(data));
                        } else
                            return;
                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                    try {
                        mByteReceivedBackSemaphore.wait(100);
                        if (mByteReceivedBack == true) {

                        } else {

                        }

                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    private class SendingThread extends Thread {
        @Override
        public void run() {
            while (!isInterrupted()) {
                synchronized (mByteReceivedBackSemaphore) {
                    mByteReceivedBack = false;
                    try {
                        if (mOutputStream != null) {
                            LogUtils.e("send" + mValueToSend);
                            mOutputStream.write(mValueToSend);
                        } else {
                            return;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                    mOutgoing++;
                    // Wait for 100ms before sending next byte, or as soon as
                    // the sent byte has been read back.
                    try {
                        mByteReceivedBackSemaphore.wait(100);
                        if (mByteReceivedBack == true) {
                            // Byte has been received
                            mIncoming++;
                        } else {
                            // Timeout
                            mLost++;
                        }
                        runOnUiThread(new Runnable() {
                            public void run() {
                                mTextViewOutgoing.setText(mOutgoing.toString());
                                mTextViewLost.setText(mLost.toString());
                                mTextViewIncoming.setText(mIncoming.toString());
                                mTextViewCorrupted.setText(mCorrupted.toString());
                            }
                        });
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loopback);
        mTextViewOutgoing = (TextView) findViewById(R.id.TextViewOutgoingValue);
        mTextViewIncoming = (TextView) findViewById(R.id.TextViewIncomingValue);
        mTextViewLost = (TextView) findViewById(R.id.textViewLostValue);
        mTextViewCorrupted = (TextView) findViewById(R.id.textViewCorruptedValue);
        if (mSerialPort != null) {
//            mSendingThread = new SendingThread();
//            mSendingThread.start();
            new SendingThread2().start();

        }
        findViewById(R.id.btn_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    blockingQueue.put(new byte[] {1});
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        new Thread(){
            @Override
            public void run() {
                while (true) {
                    try {
                        blockingQueue.put(payload);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    try {
                        sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    @Override
    protected void onDataReceived(byte[] buffer, int size) {

        synchronized (mByteReceivedBackSemaphore) {
            int i;
            for (i = 0; i < size; i++) {
                if ((buffer[i] == mValueToSend) && (mByteReceivedBack == false)) {
                    mValueToSend++;
                    // This byte was expected
                    // Wake-up the sending thread
                    mByteReceivedBack = true;
                    mByteReceivedBackSemaphore.notify();
                } else {
                    // The byte was not expected
                    mCorrupted++;
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (mSendingThread != null) mSendingThread.interrupt();
        super.onDestroy();
    }
}
