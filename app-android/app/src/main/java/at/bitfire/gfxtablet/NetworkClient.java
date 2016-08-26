package at.bitfire.gfxtablet;

public class NetworkClient {
    public final static int GFXTABLET_PORT = 40118;
    public final static byte TYPE_MOTION = 0;
    public final static byte TYPE_BUTTON = 1;

    static {
        System.loadLibrary("gfxtablet");
    }

    public native boolean create();

    public native void destroy();

    public native boolean setNetworkConfig(String ip);

    public native void sendEvent(byte type, float x, float y, float pressure, int button, boolean buttonDown);

    public native void sendEvent(byte type, float x, float y, float pressure);

    public native void setAreaSize(int width, int height);
}
