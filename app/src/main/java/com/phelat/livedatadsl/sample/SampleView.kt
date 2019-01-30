package com.phelat.livedatadsl.sample

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.phelat.livedatadsl.model.FunctionResult

class SampleView : AppCompatActivity() {

    private lateinit var functionResult: FunctionResult<Observer<String>, LiveData<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewModelProviders.of(this)[SampleViewModel_DSL::class.java].apply {
            sampleLiveData(this@SampleView) {
                println("I'm a string $it")
            }
            functionResult = sampleLiveData {
                println("I'm a string $it")
            }
            sampleLiveDataInt(this@SampleView) {
                println("I'm an integer ${it + 2}")
            }
            extendedLiveDataSample(this@SampleView) {
                println("I'm a boolean and I'm $it")
            }
            helloWorld {
                println("Hello World! $it")
            }
            liveDataWithoutReceiver {
                println("Executed without receiver")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        functionResult.apply { liveData.removeObserver(observer) }
    }

}
