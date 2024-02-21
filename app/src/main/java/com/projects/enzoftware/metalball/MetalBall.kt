package com.projects.enzoftware.metalball

import android.app.Service
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore

class MetalBall : AppCompatActivity(), SensorEventListener {

    private var mSensorManager: SensorManager? = null
    private var mAccelerometer: Sensor? = null
    private var ground: GroundView? = null

    // Evento de iniciar el movimiento y la grabacion con el botón start
    private lateinit var startButton: Button
    private var isRunning: Boolean = false
    private var isGameStarted: Boolean = false


    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccelerometer = mSensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_IMMERSIVE
        }

        // Inicializar Firestore
        firestore = FirebaseFirestore.getInstance()

        // Agregar el botón de inicio
        startButton = Button(this)
        startButton.text = "Start"
        startButton.setOnClickListener {
            if (!isRunning) {
                startButton.text = "Stop"
                isRunning = true
                ground?.startGame()
            } else {
                startButton.text = "Start"
                isRunning = false
                ground?.stopGame()
            }
        }

        // Crear un diseño lineal para colocar el botón y la vista del juego
        val layout: LinearLayout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.addView(startButton)

        // Crear la vista del juego
        ground = GroundView(this)

        // Añadir la vista del juego al diseño lineal
        layout.addView(ground)

        // Establecer el diseño lineal como contenido de la actividad
        setContentView(layout)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            ground!!.updateMe(event.values[1], event.values[0])
        }
    }

    override fun onResume() {
        super.onResume()
        mSensorManager!!.registerListener(
                this,
                mAccelerometer,
                SensorManager.SENSOR_DELAY_GAME
        )
    }

    override fun onPause() {
        super.onPause()
        mSensorManager!!.unregisterListener(this)
    }


}
class GroundView(context: Context?) : SurfaceView(context), SurfaceHolder.Callback {

    private var isGameRunning: Boolean = false
    val coordinatesList = mutableListOf<Pair<Float, Float>>()
    private var currentGameId: String? = null
    private val db = FirebaseFirestore.getInstance()

    var cx: Float = 10.toFloat()
    var cy: Float = 10.toFloat()
    var lastGx: Float = 0.toFloat()
    var lastGy: Float = 0.toFloat()

    var picHeight: Int = 0
    var picWidth: Int = 0
    var icon: Bitmap? = null

    var Windowwidth: Int = 0
    var Windowheight: Int = 0

    var noBorderX = false
    var noBorderY = false
    var vibratorService: Vibrator? = null
    var thread: DrawThread? = null

    init {
        holder.addCallback(this)
        thread = DrawThread(holder, this)
        val display: Display =
                (getContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val size: Point = Point()
        display.getSize(size)
        Windowwidth = size.x
        Windowheight = size.y
        icon = BitmapFactory.decodeResource(resources, R.drawable.ball)
        picHeight = icon!!.height
        picWidth = icon!!.width
        vibratorService = (getContext().getSystemService(Service.VIBRATOR_SERVICE)) as Vibrator
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread!!.setRunning(true)
        thread!!.start()
    }

    // Nueva función para iniciar el juego
    fun startGame() {
        isGameRunning = true
        // Iniciar la grabación de coordenadas
        coordinatesList.clear()

        // Crear un nuevo juego en Firestore
        val newGame = hashMapOf(
                "start" to System.currentTimeMillis()
        )

        db.collection("coordinates")
                .add(newGame)
                .addOnSuccessListener { documentReference ->
                    println("Game created with ID: ${documentReference.id}")
                    currentGameId = documentReference.id
                }
                .addOnFailureListener { e ->
                    println("Error creating game: $e")
                }
    }

    // Nueva función para detener el juego
    fun stopGame() {
        isGameRunning = false
        // Detener la grabación de coordenadas
        printCoordinates()
    }

    // Nueva función para imprimir las coordenadas al finalizar el juego
    private fun printCoordinates() {
        currentGameId?.let { gameId ->
            val coordinatesData = coordinatesList.map { mapOf("x" to it.first, "y" to it.second) }

            db.collection("coordinates").document(gameId)
                    .collection("games")
                    .add(mapOf("timestamp" to System.currentTimeMillis(), "coordinates" to coordinatesData))
                    .addOnSuccessListener {
                        println("Coordinates added successfully!")
                    }
                    .addOnFailureListener { e ->
                        println("Error adding coordinates: $e")
                    }
        }
    }

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        if (canvas != null && isGameRunning) {
            canvas.drawColor(0xFFAAAAA)
            icon?.let { canvas.drawBitmap(it, cx, cy, null) }
        }
    }

    fun updateMe(inx: Float, iny: Float) {
        if (isGameRunning) {
            coordinatesList.add(Pair(cx, cy))
        }

        lastGx += inx
        lastGy += iny

        cx += lastGx
        cy += lastGy

        if (cx > (Windowwidth - picWidth)) {
            cx = (Windowwidth - picWidth).toFloat()
            lastGx = 0F
            if (noBorderX) {
                vibratorService!!.vibrate(100)
                noBorderX = false
            }
        } else if (cx < (0)) {
            cx = 0F
            lastGx = 0F
            if (noBorderX) {
                vibratorService!!.vibrate(100)
                noBorderX = false
            }
        } else {
            noBorderX = true
        }

        if (cy > (Windowheight - picHeight)) {
            cy = (Windowheight - picHeight).toFloat()
            lastGy = 0F
            if (noBorderY) {
                vibratorService!!.vibrate(100)
                noBorderY = false
            }
        } else if (cy < (0)) {
            cy = 0F
            lastGy = 0F
            if (noBorderY) {
                vibratorService!!.vibrate(100)
                noBorderY = false
            }
        } else {
            noBorderY = true
        }

        invalidate()
    }
}

class DrawThread(surfaceHolder: SurfaceHolder, panel: GroundView) : Thread() {
    private var surfaceHolder: SurfaceHolder? = null
    private var panel: GroundView? = null
    private var run = false

    init {
        this.surfaceHolder = surfaceHolder
        this.panel = panel
    }

    fun setRunning(run: Boolean) {
        this.run = run
    }

    override fun run() {
        var c: Canvas? = null
        while (run) {
            c = null
            try {
                c = surfaceHolder!!.lockCanvas(null)
                synchronized(surfaceHolder!!) {
                    panel!!.draw(c)
                }
            } finally {
                if (c != null) {
                    surfaceHolder!!.unlockCanvasAndPost(c)
                }
            }
        }
    }
}
