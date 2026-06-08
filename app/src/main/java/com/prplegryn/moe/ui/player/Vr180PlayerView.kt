package com.prplegryn.moe.ui.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Vr180PlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {
    var onSurfaceAvailable: ((Surface) -> Unit)? = null

    private val renderer = Vr180Renderer(
        requestFrame = { requestRender() },
        publishSurface = { surface -> post { onSurfaceAvailable?.invoke(surface) } },
    )

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setProjection(value: Float) {
        renderer.projection = value.coerceIn(0f, 1f)
        requestRender()
    }

    fun setLook(yawRadians: Float, pitchRadians: Float) {
        renderer.yawRadians = yawRadians.coerceIn(-1.35f, 1.35f)
        renderer.pitchRadians = pitchRadians.coerceIn(-0.7f, 0.7f)
        requestRender()
    }

    fun releaseVideoSurface() {
        queueEvent { renderer.releaseSurface() }
    }
}

private class Vr180Renderer(
    private val requestFrame: () -> Unit,
    private val publishSurface: (Surface) -> Unit,
) : GLSurfaceView.Renderer {
    @Volatile
    var projection: Float = 0.54f

    @Volatile
    var yawRadians: Float = 0f

    @Volatile
    var pitchRadians: Float = 0f

    private val vertices = floatBufferOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f,
    )
    private val textureTransform = FloatArray(16)
    private val frameLock = Any()

    private var program = 0
    private var textureId = 0
    private var width = 1
    private var height = 1
    private var frameAvailable = false
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        Matrix.setIdentityM(textureTransform, 0)
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        textureId = createExternalTexture()
        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener {
                synchronized(frameLock) {
                    frameAvailable = true
                }
                requestFrame()
            }
        }
        surface = Surface(surfaceTexture).also(publishSurface)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, this.width, this.height)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        val texture = surfaceTexture ?: return
        synchronized(frameLock) {
            if (frameAvailable) {
                texture.updateTexImage()
                texture.getTransformMatrix(textureTransform)
                frameAvailable = false
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertices)

        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, textureTransform, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uAspect"), width.toFloat() / height.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uProjection"), projection)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uYaw"), yawRadians)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uPitch"), pitchRadians)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
    }

    fun releaseSurface() {
        surface?.release()
        surface = null
        surfaceTexture?.release()
        surfaceTexture = null
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }
}

private fun createExternalTexture(): Int {
    val textures = IntArray(1)
    GLES20.glGenTextures(1, textures, 0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    return textures[0]
}

private fun buildProgram(vertexSource: String, fragmentSource: String): Int {
    val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
    val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
    val program = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vertexShader)
    GLES20.glAttachShader(program, fragmentShader)
    GLES20.glLinkProgram(program)
    val status = IntArray(1)
    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
    if (status[0] == 0) {
        val log = GLES20.glGetProgramInfoLog(program)
        GLES20.glDeleteProgram(program)
        error("Could not link VR180 shader program: $log")
    }
    GLES20.glDeleteShader(vertexShader)
    GLES20.glDeleteShader(fragmentShader)
    return program
}

private fun compileShader(type: Int, source: String): Int {
    val shader = GLES20.glCreateShader(type)
    GLES20.glShaderSource(shader, source)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
        val log = GLES20.glGetShaderInfoLog(shader)
        GLES20.glDeleteShader(shader)
        error("Could not compile VR180 shader: $log")
    }
    return shader
}

private fun floatBufferOf(vararg values: Float): FloatBuffer {
    return ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }
}

private const val VERTEX_SHADER = """
attribute vec2 aPosition;
varying vec2 vScreen;

void main() {
    vScreen = (aPosition + 1.0) * 0.5;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

private const val FRAGMENT_SHADER = """
#extension GL_OES_EGL_image_external : require
precision highp float;

uniform samplerExternalOES uTexture;
uniform mat4 uTexMatrix;
uniform float uAspect;
uniform float uProjection;
uniform float uYaw;
uniform float uPitch;

varying vec2 vScreen;

const float PI = 3.14159265358979323846264;

float radiansFromDegrees(float degreesValue) {
    return degreesValue * PI / 180.0;
}

void main() {
    float projection = clamp(uProjection, 0.0, 1.0);
    float fov = mix(radiansFromDegrees(58.0), radiansFromDegrees(96.0), projection);
    float lens = tan(fov * 0.5);
    vec2 p = vScreen * 2.0 - 1.0;
    vec3 ray = normalize(vec3(p.x * uAspect * lens, -p.y * lens, -1.0));

    float yaw = atan(ray.x, -ray.z) + uYaw;
    float pitch = asin(clamp(ray.y, -1.0, 1.0)) + uPitch;

    if (abs(yaw) > PI * 0.5 || abs(pitch) > PI * 0.5) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float eyeU = yaw / PI + 0.5;
    float eyeV = 0.5 - pitch / PI;
    vec2 texCoord = vec2(eyeU * 0.5, eyeV);
    vec2 transformed = (uTexMatrix * vec4(texCoord, 0.0, 1.0)).xy;
    gl_FragColor = texture2D(uTexture, transformed);
}
"""
