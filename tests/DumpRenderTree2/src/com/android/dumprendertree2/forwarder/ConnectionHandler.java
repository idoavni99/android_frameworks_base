/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dumprendertree2.forwarder;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Worker class for {@link Forwarder}. A ConnectionHandler will be created once the Forwarder
 * accepts an incoming connection, and it will then forward the incoming/outgoing streams to a
 * connection already proxied by adb networking (see also {@link AdbUtils}).
 */
public class ConnectionHandler {

    private static final String LOG_TAG = "ConnectionHandler";

    public static interface OnFinishedCallback {
        public void onFinished();
    }

    private class SocketPipeThread extends Thread {

        private Socket mInSocket, mOutSocket;

        public SocketPipeThread(Socket inSocket, Socket outSocket) {
            mInSocket = inSocket;
            mOutSocket = outSocket;
        }

        @Override
        public void run() {
            InputStream is;
            OutputStream os;
            try {
                synchronized (this) {
                    is = mInSocket.getInputStream();
                    os = mOutSocket.getOutputStream();
                }
            } catch (IOException e) {
                Log.w(LOG_TAG, this.toString(), e);
                finish();
                return;
            }

            byte[] buffer = new byte[4096];
            int length;
            while (true) {
                try {
                    if ((length = is.read(buffer)) <= 0) {
                        break;
                    }
                    os.write(buffer, 0, length);
                } catch (IOException e) {
                    /** This exception means one of the streams is closed */
                    Log.v(LOG_TAG, this.toString(), e);
                    break;
                }
            }

            finish();
        }

        private void finish() {
            synchronized (mThreadsRunning) {
                mThreadsRunning--;
                if (mThreadsRunning == 0) {
                    ConnectionHandler.this.stop();
                    mOnFinishedCallback.onFinished();
                }
            }
        }

        @Override
        public String toString() {
            return "SocketPipeThread:\n" + mInSocket + "\n=>\n" + mOutSocket;
        }
    }

    private Integer mThreadsRunning;

    private Socket mFromSocket, mToSocket;
    private SocketPipeThread mFromToPipe, mToFromPipe;

    private OnFinishedCallback mOnFinishedCallback;

    public ConnectionHandler(Socket fromSocket, Socket toSocket) {
        mFromSocket = fromSocket;
        mToSocket = toSocket;
        mFromToPipe = new SocketPipeThread(mFromSocket, mToSocket);
        mToFromPipe = new SocketPipeThread(mToSocket, mFromSocket);
    }

    public void registerOnConnectionHandlerFinishedCallback(OnFinishedCallback callback) {
        mOnFinishedCallback = callback;
    }

    public void start() {
        /** We have 2 threads running, one for each pipe, that we start here. */
        mThreadsRunning = 2;
        mFromToPipe.start();
        mToFromPipe.start();
    }

    public void stop() {
        shutdown(mFromSocket);
        shutdown(mToSocket);
    }

    private void shutdown(Socket socket) {
        synchronized (mFromToPipe) {
            synchronized (mToFromPipe) {
                /** This will stop the while loop in the run method */
                try {
                    if (!socket.isInputShutdown()) {
                        socket.shutdownInput();
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "mFromToPipe=" + mFromToPipe + " mToFromPipe=" + mToFromPipe, e);
                }
                try {
                    if (!socket.isOutputShutdown()) {
                        socket.shutdownOutput();
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "mFromToPipe=" + mFromToPipe + " mToFromPipe=" + mToFromPipe, e);
                }
                try {
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "mFromToPipe=" + mFromToPipe + " mToFromPipe=" + mToFromPipe, e);
                }
            }
        }
    }
}