package com.phelat.livedatadsl.sample

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AppCompatActivity

class SampleView : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewModelProviders.of(this)[SampleViewModel_DSL::class.java].apply {
            sampleLiveData(this@SampleView) {
                println("I'm a string $it")
            }
            sampleLiveData {
                println("I'm a string $it")
            }
            sampleLiveDataInt(this@SampleView) {
                println("I'm an integer ${it + 2}")
            }
            extendedLiveDataSample(this@SampleView) {
                println("I'm a boolean and I'm $it")
            }
        }
    }

}
