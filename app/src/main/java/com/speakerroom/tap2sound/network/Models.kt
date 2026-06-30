package com.speakerroom.tap2sound.network

// Auth Models
data class RegisterRequest(
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val user: UserData,
    val token: String
)

data class UserData(
    val id: String,
    val email: String,
    val isAdmin: Boolean = false
)

// Speaker Models
data class AutoPairRequest(
    val nfcUid: String,
    val btMac: String,
    val name: String? = null
)

data class AutoPairResponse(
    val success: Boolean,
    val speaker: SpeakerResponse
)

data class SpeakerResponse(
    val id: String,
    val nfcUid: String,
    val btMac: String,
    val name: String? = null
)

data class GetSpeakersResponse(
    val speakers: List<SpeakerResponse>
)

data class RenameSpeakerRequest(
    val speakerId: String,
    val name: String
)

data class RenameSpeakerResponse(
    val success: Boolean,
    val speaker: SpeakerResponse
)

// Admin: registro de etiqueta NFC (lista blanca)
data class RegisterTagRequest(
    val nfcUid: String,
    val name: String? = null
)

data class RegisterTagResponse(
    val success: Boolean,
    val alreadyRegistered: Boolean = false,
    val speaker: SpeakerResponse? = null
)

// Estado de un tag NFC (lista blanca)
data class TagStatusResponse(
    val registered: Boolean = false,
    val paired: Boolean = false
)

// Error responses
data class ErrorResponse(
    val error: String
)
