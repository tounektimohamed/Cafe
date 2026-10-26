package com.example

import com.example.core.model.OrderState
import com.example.core.model.OrderStateMachine
import com.example.core.model.TransitionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderStateMachineTest {

    private val stateMachine = OrderStateMachine(paymentFirstDefault = true)

    @Test
    fun testPaymentFirstStandardFlow() {
        // NEW -> AWAITING_PAYMENT
        val step1 = stateMachine.canTransition(OrderState.NEW, OrderState.AWAITING_PAYMENT, paymentFirst = true)
        assertTrue(step1 is TransitionResult.Success)
        val s1 = step1 as TransitionResult.Success
        assertEquals(OrderState.AWAITING_PAYMENT, s1.newState)
        assertEquals(false, s1.requiresPrintTicket)

        // Illegal transition: NEW directly to PAID without request
        val illegal = stateMachine.canTransition(OrderState.NEW, OrderState.PAID, paymentFirst = true)
        assertTrue(illegal is TransitionResult.Error)

        // AWAITING_PAYMENT -> PAID
        val step2 = stateMachine.canTransition(OrderState.AWAITING_PAYMENT, OrderState.PAID, paymentFirst = true)
        assertTrue(step2 is TransitionResult.Success)
        val s2 = step2 as TransitionResult.Success
        assertEquals(OrderState.PAID, s2.newState)
        // Preparation ticket must print automatically upon reaching PAID!
        assertEquals(true, s2.requiresPrintTicket)

        // PAID -> READY
        val step3 = stateMachine.canTransition(OrderState.PAID, OrderState.READY, paymentFirst = true)
        assertTrue(step3 is TransitionResult.Success)
        val s3 = step3 as TransitionResult.Success
        assertEquals(OrderState.READY, s3.newState)

        // READY -> SERVED
        val step4 = stateMachine.canTransition(OrderState.READY, OrderState.SERVED, paymentFirst = true)
        assertTrue(step4 is TransitionResult.Success)
        val s4 = step4 as TransitionResult.Success
        assertEquals(OrderState.SERVED, s4.newState)

        // SERVED is terminal: cannot transition further
        val terminalAttempt = stateMachine.canTransition(OrderState.SERVED, OrderState.NEW, paymentFirst = true)
        assertTrue(terminalAttempt is TransitionResult.Error)
    }

    @Test
    fun testCancellationRequiresReasonAndManagerApprovalAfterPaid() {
        // Without reason -> Error
        val noReason = stateMachine.canTransition(
            OrderState.NEW,
            OrderState.CANCELLED,
            paymentFirst = true,
            cancellationReason = ""
        )
        assertTrue(noReason is TransitionResult.Error)

        // With reason from NEW -> Success (no manager pin needed)
        val withReason = stateMachine.canTransition(
            OrderState.NEW,
            OrderState.CANCELLED,
            paymentFirst = true,
            cancellationReason = "الزبون غير رأيه وغادر"
        )
        assertTrue(withReason is TransitionResult.Success)

        // From PAID without manager approval -> Error
        val paidCancelNoApproval = stateMachine.canTransition(
            OrderState.PAID,
            OrderState.CANCELLED,
            paymentFirst = true,
            cancellationReason = "خطأ في تسجيل الطلب",
            isManagerApproved = false
        )
        assertTrue(paidCancelNoApproval is TransitionResult.Error)

        // From PAID with manager approval -> Success
        val paidCancelApproved = stateMachine.canTransition(
            OrderState.PAID,
            OrderState.CANCELLED,
            paymentFirst = true,
            cancellationReason = "خطأ في تسجيل الطلب ومصادقة المدير",
            isManagerApproved = true
        )
        assertTrue(paidCancelApproved is TransitionResult.Success)
    }

    @Test
    fun testStuckAwaitingPaymentDetection() {
        val now = 1000000L
        val fiveMinutesAndOneSecondAgo = now - (5 * 60 * 1000L + 1000L)
        val twoMinutesAgo = now - (2 * 60 * 1000L)

        // Stuck > 5 min
        val isStuck = stateMachine.isStuckAwaitingPayment(
            OrderState.AWAITING_PAYMENT,
            updatedAtMillis = fiveMinutesAndOneSecondAgo,
            currentTimeMillis = now
        )
        assertTrue(isStuck)

        // Not stuck (< 5 min)
        val notStuck = stateMachine.isStuckAwaitingPayment(
            OrderState.AWAITING_PAYMENT,
            updatedAtMillis = twoMinutesAgo,
            currentTimeMillis = now
        )
        assertTrue(!notStuck)
    }
}
