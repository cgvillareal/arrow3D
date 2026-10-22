package com.arrow3d.game.render

import com.arrow3d.domain.surface.Vec3i
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Pure math, no Android imports: unit-testable on the JVM.

data class Vec3f(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3f) = Vec3f(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3f) = Vec3f(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec3f(x * k, y * k, z * k)
    infix fun dot(o: Vec3f) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3f) = Vec3f(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    fun normalized(): Vec3f {
        val l = length()
        return if (l == 0f) this else this * (1f / l)
    }

    companion object {
        fun of(v: Vec3i) = Vec3f(v.x.toFloat(), v.y.toFloat(), v.z.toFloat())
    }
}

data class Point2(val x: Float, val y: Float) {
    operator fun minus(o: Point2) = Point2(x - o.x, y - o.y)
    fun distanceTo(o: Point2): Float {
        val dx = x - o.x
        val dy = y - o.y
        return sqrt(dx * dx + dy * dy)
    }
}

/** Camera orientation. Defaults show TOP, FRONT and RIGHT faces at once (design doc, section 11). */
data class CameraAngles(val yawDeg: Float = DEFAULT_YAW, val pitchDeg: Float = DEFAULT_PITCH, val zoom: Float = 1f) {
    companion object {
        const val DEFAULT_YAW = -45f
        const val DEFAULT_PITCH = 30f
        const val MIN_PITCH = -85f
        const val MAX_PITCH = 85f
        const val MIN_ZOOM = 0.7f
        const val MAX_ZOOM = 1.8f
    }
}

/**
 * Perspective projection of the cube surface onto the screen.
 * Input points are in the domain's doubled units ([0, 2n] per axis); the cube is centred at the
 * origin with half-size 1 in world units. The camera sits on +Z looking at the origin.
 */
class CubeProjector(
    val width: Float,
    val height: Float,
    val gridSize: Int,
    angles: CameraAngles,
) {
    private val yaw = Math.toRadians(angles.yawDeg.toDouble())
    private val pitch = Math.toRadians(angles.pitchDeg.toDouble())
    private val cy = cos(yaw).toFloat()
    private val sy = sin(yaw).toFloat()
    private val cp = cos(pitch).toFloat()
    private val sp = sin(pitch).toFloat()
    private val centreX = width / 2f
    private val centreY = height / 2f
    private val focal = FIT * min(width, height) * CAMERA_DISTANCE * angles.zoom
    val camera = Vec3f(0f, 0f, CAMERA_DISTANCE)

    /** Doubled surface units -> world space (unrotated). */
    fun toWorld(p: Vec3f): Vec3f {
        val n = gridSize.toFloat()
        return Vec3f((p.x - n) / n, (p.y - n) / n, (p.z - n) / n)
    }

    /** Applies yaw (around Y) then pitch (around X). Works for points and directions. */
    fun rotate(v: Vec3f): Vec3f {
        val x1 = v.x * cy + v.z * sy
        val z1 = -v.x * sy + v.z * cy
        val y2 = v.y * cp - z1 * sp
        val z2 = v.y * sp + z1 * cp
        return Vec3f(x1, y2, z2)
    }

    /** Doubled surface point -> view space. */
    fun view(p: Vec3f): Vec3f = rotate(toWorld(p))

    fun project(viewPoint: Vec3f): Point2 {
        val depth = CAMERA_DISTANCE - viewPoint.z
        return Point2(centreX + focal * viewPoint.x / depth, centreY - focal * viewPoint.y / depth)
    }

    fun projectSurface(p: Vec3f): Point2 = project(view(p))

    fun projectSurface(p: Vec3i): Point2 = projectSurface(Vec3f.of(p))

    /** True when a surface with this (unrotated) normal, at this doubled point, faces the camera. */
    fun isFacing(normal: Vec3i, at: Vec3f): Boolean {
        val n = rotate(Vec3f.of(normal))
        return (n dot (camera - view(at))) > FACING_EPSILON
    }

    /** Lambert factor in [0, 1] for face shading, light fixed relative to the camera. */
    fun light(normal: Vec3i): Float = (rotate(Vec3f.of(normal)) dot LIGHT).coerceIn(0f, 1f)

    /** Approximate on-screen size of one cell near the cube centre, in pixels. */
    val cellPixels: Float get() = focal * (2f / gridSize) / CAMERA_DISTANCE

    companion object {
        const val CAMERA_DISTANCE = 4.5f
        private const val FIT = 0.19f
        private const val FACING_EPSILON = 0.02f
        private val LIGHT = Vec3f(-0.35f, 0.8f, 0.5f).normalized()
    }
}
