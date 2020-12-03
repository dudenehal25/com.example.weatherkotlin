package com.example.weatherkotlin.models

import java.io.Serializable

data class Wind(
    val speed:Double,
    val deg:Int
) :Serializable