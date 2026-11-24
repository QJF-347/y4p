package org.whoismacy.mainconsumapp.ui.navigation

import kotlinx.serialization.Serializable
import org.whoismacy.mainconsumapp.data.model.CartItem

sealed class Route {
    @Serializable
    data object Pos : Route()
    
    // In a real app we wouldn't pass a list of complex objects in the route
    // However, for this phase mock, we'll pass cartItems through a shared ViewModel or just mock it in Checkout.
    // For now, let's revert back to totalAmount and just mock CartItem passing to keep things simple
    @Serializable
    data class Checkout(val totalAmount: Double) : Route()
    
    @Serializable
    data object Products : Route()
    
    @Serializable
    data object History : Route()

    @Serializable
    data object Setup : Route()
}
