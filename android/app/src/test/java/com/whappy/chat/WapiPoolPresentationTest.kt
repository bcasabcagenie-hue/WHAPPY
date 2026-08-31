package com.whappy.chat

import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WapiPoolPresentationTest {
    @Test fun strategyCameraKeepsKingsAndBoardInFrameAcrossRotations() {
        for (aspect in listOf(.45f, 1f, 2.22f)) for (pitchDegrees in listOf(37f, 49f, 67f)) for (yawDegrees in listOf(-27f, 0f, 27f)) {
            val pitch = Math.toRadians(pitchDegrees.toDouble()); val yaw = Math.toRadians(yawDegrees.toDouble())
            val radius = WapiPoolPresentation.strategyCameraDistance(aspect, pitchDegrees, yawDegrees)
            for (x in listOf(-4.72, 4.72)) for (z in listOf(-4.72, 4.72)) for (y in listOf(-.5, 1.7)) {
                val depth = radius - x * sin(yaw) * cos(pitch) - y * sin(pitch) - z * cos(yaw) * cos(pitch)
                val h = x * cos(yaw) - z * sin(yaw)
                val v = -x * sin(yaw) * sin(pitch) + y * cos(pitch) - z * cos(yaw) * sin(pitch)
                assertTrue(abs(h / (depth * tan(Math.toRadians(19.0)) * aspect)) < 1)
                assertTrue(abs(v / (depth * tan(Math.toRadians(19.0)))) < 1)
            }
        }
    }
    @Test fun cameraKeepsAllRailCornersVisibleOnWideAndFoldableScreens() {
        val pitch = Math.toRadians(WapiPoolPresentation.pitchDegrees.toDouble())
        val tangent = tan(Math.toRadians(WapiPoolPresentation.fieldOfViewDegrees / 2.0))
        for (aspect in listOf(.6f, 1f, 1.33f, 1.78f, 2.22f, 2.5f)) {
            val distance = WapiPoolPresentation.cameraDistance(aspect)
            for (x in listOf(-5.4f, 5.4f)) for (z in listOf(-2.9f, 2.9f)) for (y in listOf(-.55f, .5f)) {
                val depth = distance - y * sin(pitch) - z * cos(pitch)
                val vertical = y * cos(pitch) - z * sin(pitch)
                assertTrue("Rail cropped horizontally at aspect $aspect", abs(x / (depth * tangent * aspect)) < 1)
                assertTrue("Rail cropped vertically at aspect $aspect", abs(vertical / (depth * tangent)) < 1)
            }
        }
    }

    @Test fun sixOpeningsMatchThePhysicsPocketCentres() {
        val expected = listOf(.055f to .07f, .5f to .07f, .945f to .07f, .055f to .93f, .5f to .93f, .945f to .93f)
        assertEquals(6, WapiPoolPresentation.pockets.distinct().size)
        expected.zip(WapiPoolPresentation.pockets).forEach { (normalized, world) ->
            assertEquals((normalized.first - .5f) * WAPI_POOL_WORLD_WIDTH, world.first, .00001f)
            assertEquals((normalized.second - .5f) * WAPI_POOL_WORLD_HEIGHT, world.second, .00001f)
        }
    }

    @Test fun diagonalAimUsesPhysicalTableAspectRatio() {
        assertEquals((Math.PI / 4).toFloat(), WapiPoolPresentation.aimAngle(.10f, .20f), .00001f)
        assertEquals(0f, WapiPoolPresentation.aimAngle(.10f, 0f), .00001f)
    }

    @Test fun leatherLipDoesNotCoverThePocketOpening() {
        val innerLip = WapiPoolPresentation.pocketLeatherRadius * (.96f - .04f)
        assertTrue(abs(innerLip - WapiPoolPresentation.pocketOpeningRadius) < .001f)
        assertTrue(WapiPoolPresentation.pocketLeatherRadius - innerLip < .03f)
    }

    @Test fun visibleMeshFacesAgreeWithTheirNormals() {
        // Exercise the exact CPU mesh factories without an EGL context. A
        // reversed triangle gets culled on phones even when its normal is right.
        val renderer = Class.forName("com.whappy.chat.WapiTabletop3DView\$TabletopRenderer")
        val factory = renderer.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
        val recipes = listOf(
            "railMesh" to emptyArray<Any>(),
            "poolCushionMesh" to emptyArray<Any>(),
            "chessStemMesh" to arrayOf<Any>(64),
            "cylinderMesh" to arrayOf<Any>(48), "coneMesh" to arrayOf<Any>(48),
            "frustumMesh" to arrayOf<Any>(48, .43f), "sphereMesh" to arrayOf<Any>(40, 24),
            "torusMesh" to arrayOf<Any>(48, 14, .78, .22), "pocketLinerMesh" to arrayOf<Any>(48),
        )
        recipes.forEach { (name, args) ->
            val method = factory.javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }
            method.isAccessible = true
            val mesh = method.invoke(factory, *args)
            val field = mesh.javaClass.getDeclaredField("vertices").apply { isAccessible = true }
            val data = (field.get(mesh) as FloatBuffer).duplicate().apply { rewind() }
            val triangle = FloatArray(18)
            while (data.remaining() >= triangle.size) {
                data.get(triangle)
                val ax = triangle[6] - triangle[0]; val ay = triangle[7] - triangle[1]; val az = triangle[8] - triangle[2]
                val bx = triangle[12] - triangle[0]; val by = triangle[13] - triangle[1]; val bz = triangle[14] - triangle[2]
                val nx = ay * bz - az * by; val ny = az * bx - ax * bz; val nz = ax * by - ay * bx
                val normalDot = nx * triangle[3] + ny * triangle[4] + nz * triangle[5]
                assertTrue("Backwards face in $name", normalDot >= -.000001f)
            }
        }
    }
}
