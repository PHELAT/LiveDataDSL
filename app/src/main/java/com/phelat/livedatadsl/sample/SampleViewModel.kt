package com.phelat.livedatadsl.sample

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.phelat.livedatadsl.annotation.LiveDataDSL

open class SampleViewModel : ViewModel() {

    @LiveDataDSL
    protected val sampleLiveData = MutableLiveData<String>()

    @LiveDataDSL
    protected val sampleLiveDataInt = MutableLiveData<Int>()

    @LiveDataDSL
    protected val extendedLiveDataSample = ExtendedLiveData()

    @LiveDataDSL("helloWorld")
    protected val liveDataWithCustomName = MutableLiveData<Long>()
    
    @LiveDataDSL
    protected val liveDataWithoutReceiver = MutableLiveData<Unit>()

    class ExtendedLiveData : MutableLiveData<Boolean>()

}
