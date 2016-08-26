#include <netdb.h>
#include <unistd.h>
#include <limits.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <errno.h>
#include <android/log.h>
#include <stdio.h>
#include "protocol.h"
#include "at_bitfire_gfxtablet_NetworkClient.h"

static int sock = 0;
static struct sockaddr_in host;
static event_packet packet;
static const size_t packetSizeFull = sizeof(packet);
static const size_t packetSizeShort = sizeof(packet) - sizeof(packet.button) - sizeof(packet.down);
static jfloat width = 1;
static jfloat height = 1;

template <typename T>
inline T clamp(T min, T val, T max)
{
    return (val < min) ? min : ((val > max) ? max : val);
}

inline uint16_t normalize(jfloat val, jfloat limit) {
    return static_cast<uint16_t>(clamp(0.f, val / limit, 1.f) * USHRT_MAX);
}

jboolean JNICALL Java_at_bitfire_gfxtablet_NetworkClient_create(JNIEnv *, jobject) {
    if (sock) {
        close(sock);
        sock = 0;
    }

    sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock < 0) {
        sock = 0;
        perror("cannot create socket");
        return JNI_FALSE;
    }

    memcpy(packet.signature, "GfxTablet", 9);
    packet.version = htons(PROTOCOL_VERSION);

    return JNI_TRUE;
}

void JNICALL Java_at_bitfire_gfxtablet_NetworkClient_destroy(JNIEnv *, jobject) {
    if (sock) {
        close(sock);
        sock = 0;
    }
}

jboolean JNICALL Java_at_bitfire_gfxtablet_NetworkClient_setNetworkConfig(
        JNIEnv *env, jobject, jstring jhost) {

    jsize len = env->GetStringUTFLength(jhost);
    if (len > NI_MAXHOST) {
        __android_log_write(ANDROID_LOG_ERROR, "GFXTAB", "Hostname is too long");
        return JNI_FALSE;
    }

    char hostStr[NI_MAXHOST] = {0};
    env->GetStringUTFRegion(jhost, 0, len, hostStr);

    // get host struct
    memset(&host, 0, sizeof(host));
    host.sin_family = AF_INET;
    host.sin_port = htons(GFXTABLET_PORT);
    if (!inet_aton(hostStr, &host.sin_addr)) {
        __android_log_print(ANDROID_LOG_ERROR, "GFXTAB", "aton failed");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

void JNICALL Java_at_bitfire_gfxtablet_NetworkClient_sendEvent__BFFFIZ(
        JNIEnv *, jobject, jbyte type, jfloat x, jfloat y, jfloat pressure, jint button, jboolean buttonDown) {

    packet.type = static_cast<uint8_t>(type);
    packet.x = htons(normalize(x, width));
    packet.y = htons(normalize(y, height));
    packet.pressure = htons(normalize(pressure, 1.f));
    packet.button = static_cast<int8_t>(button);
    packet.down = buttonDown;

    if (sendto(sock, &packet, packetSizeFull, MSG_DONTWAIT,
               reinterpret_cast<const sockaddr*>(&host), sizeof(host)) == -1) {
        __android_log_print(ANDROID_LOG_ERROR, "GFXTAB", "failed to sendto: %s", strerror(errno));
    }

#if 0
    char tmp[packetSizeFull * 2 + 1];
    for (int i = 0; i < packetSizeFull; ++i) {
        snprintf(&tmp[i * 2], sizeof(tmp), "%02x", (((unsigned char*)&packet)[i]));
    }
    tmp[sizeof(tmp) - 1] = '\0';

    __android_log_print(ANDROID_LOG_INFO, "GFXTAB", "sent packet: %s", tmp);
#endif
}

void JNICALL Java_at_bitfire_gfxtablet_NetworkClient_sendEvent__BFFF(
        JNIEnv *, jobject, jbyte type, jfloat x, jfloat y, jfloat pressure) {

    packet.type = static_cast<uint8_t>(type);
    packet.x = htons(normalize(x, width));
    packet.y = htons(normalize(y, height));
    packet.pressure = htons(normalize(pressure, 1.f));

    if (sendto(sock, &packet, packetSizeShort, MSG_DONTWAIT,
               reinterpret_cast<const sockaddr*>(&host), sizeof(host)) == -1) {
        __android_log_print(ANDROID_LOG_ERROR, "GFXTAB", "failed to sendto: %s", strerror(errno));
    }
}

void JNICALL Java_at_bitfire_gfxtablet_NetworkClient_setAreaSize(
        JNIEnv *, jobject, jint jwidth, jint jheight) {
    width = static_cast<jfloat>(jwidth);
    height = static_cast<jfloat>(jheight);
}

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* jvm, void*)
{
    JNIEnv* env = NULL;
    if (jvm->GetEnv((void**)&env, JNI_VERSION_1_2))
        return JNI_ERR;

    __android_log_print(ANDROID_LOG_INFO, "GFXTAB", "libgfxtab loaded");

    return JNI_VERSION_1_2;
}

}
