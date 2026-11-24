package com.sixteenhalves.orangeadmin.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InventoryInputTest {
    @Test
    fun `whole number price becomes cents`() {
        assertEquals(120_000L, InventoryInput.priceCentsOrNull("1200"))
    }

    @Test
    fun `one decimal place price becomes cents`() {
        assertEquals(1_250L, InventoryInput.priceCentsOrNull("12.5"))
    }

    @Test
    fun `two decimal place price becomes cents`() {
        assertEquals(1_250L, InventoryInput.priceCentsOrNull("12.50"))
    }

    @Test
    fun `thousands separators are ignored`() {
        assertEquals(120_050L, InventoryInput.priceCentsOrNull("1,200.50"))
    }

    @Test
    fun `three decimal places are rejected`() {
        assertNull(InventoryInput.priceCentsOrNull("12.345"))
    }

    @Test
    fun `non numeric price is rejected`() {
        assertNull(InventoryInput.priceCentsOrNull("abc"))
        assertNull(InventoryInput.priceCentsOrNull("-5"))
        assertNull(InventoryInput.priceCentsOrNull(""))
    }

    @Test
    fun `zero price is rejected`() {
        assertNull(InventoryInput.priceCentsOrNull("0"))
        assertNull(InventoryInput.priceCentsOrNull("0.00"))
    }

    @Test
    fun `quantity accepts whole non negative numbers`() {
        assertEquals(0, InventoryInput.quantityOrNull("0"))
        assertEquals(42, InventoryInput.quantityOrNull("42"))
    }

    @Test
    fun `quantity rejects fractions negatives and text`() {
        assertNull(InventoryInput.quantityOrNull("1.5"))
        assertNull(InventoryInput.quantityOrNull("-3"))
        assertNull(InventoryInput.quantityOrNull("many"))
        assertNull(InventoryInput.quantityOrNull(""))
    }

    @Test
    fun `blank quantity defaults to zero`() {
        assertEquals(0, InventoryInput.quantityOrDefault(""))
        assertEquals(7, InventoryInput.quantityOrDefault("7"))
        assertNull(InventoryInput.quantityOrDefault("oops"))
    }
}
