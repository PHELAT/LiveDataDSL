package com.phelat.livedatadsl.sample

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.phelat.livedatadsl.LiveDataDSL

open class SampleViewModel : ViewModel() {

    @LiveDataDSL
    protected val sampleLiveData = MutableLiveData<String>()

    @LiveDataDSL
    protected val sampleLiveDataInt = MutableLiveData<Int>()

    @LiveDataDSL
    protected val extendedLiveDataSample = ExtendedLiveData()

    class ExtendedLiveData : MutableLiveData<Boolean>()

}
