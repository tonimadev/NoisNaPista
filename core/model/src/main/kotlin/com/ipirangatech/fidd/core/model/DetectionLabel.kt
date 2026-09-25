package com.ipirangatech.fidd.core.model

/**
 * Manual classification assigned by the user (debug builds only) to a captured [SensorWindow],
 * used to turn a week of raw driving data into a labeled dataset for future model training.
 */
enum class DetectionLabel(val displayName: String) {
    UNLABELED("Não classificado"),
    POTHOLE("Buraco real"),
    SPEED_BUMP("Lombada / quebra-mola"),
    ROUGH_ROAD("Pista irregular / esburacada"),
    JOINT_OR_MANHOLE("Junta, trilho ou bueiro"),
    BRAKING_OR_MANEUVER("Frenagem / manobra"),
    PHONE_HANDLING("Manuseio do celular"),
    FALSE_POSITIVE("Alarme falso / outro"),
    ;

    companion object {
        fun fromStorageValueOrDefault(value: String?): DetectionLabel =
            value?.let { stored -> entries.find { it.name == stored } } ?: UNLABELED
    }
}
