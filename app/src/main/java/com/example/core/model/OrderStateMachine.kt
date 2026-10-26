package com.example.core.model

sealed class TransitionResult {
    data class Success(val newState: OrderState, val requiresPrintTicket: Boolean) : TransitionResult()
    data class Error(val messageAr: String) : TransitionResult()
}

class OrderStateMachine(val paymentFirstDefault: Boolean = true) {

    /**
     * Determines whether the transition from [currentState] to [targetState] is valid.
     *
     * @param currentState Current order state
     * @param targetState Target order state
     * @param paymentFirst Whether payment-before-preparation setting is enabled
     * @param cancellationReason Mandatory non-empty reason when target is CANCELLED
     * @param isManagerApproved Whether manager PIN has been confirmed (required for cancellations after PAID)
     */
    fun canTransition(
        currentState: OrderState,
        targetState: OrderState,
        paymentFirst: Boolean = paymentFirstDefault,
        cancellationReason: String? = null,
        isManagerApproved: Boolean = false
    ): TransitionResult {
        if (currentState == targetState) {
            return TransitionResult.Error("الطلب في هذه الحالة بالفعل")
        }

        if (currentState.isTerminal) {
            return TransitionResult.Error("لا يمكن تعديل طلب مغلق أو ملغى")
        }

        if (targetState == OrderState.CANCELLED) {
            if (cancellationReason.isNullOrBlank()) {
                return TransitionResult.Error("سبب الإلغاء إجباري لتسجيله في سجل الرقابة")
            }
            if ((currentState == OrderState.PAID || currentState == OrderState.READY) && !isManagerApproved) {
                return TransitionResult.Error("إلغاء طلب بعد الدفع يتطلب موافقة المدير بالرمز السري")
            }
            return TransitionResult.Success(OrderState.CANCELLED, requiresPrintTicket = false)
        }

        return if (paymentFirst) {
            when (currentState) {
                OrderState.NEW -> {
                    if (targetState == OrderState.AWAITING_PAYMENT) {
                        TransitionResult.Success(OrderState.AWAITING_PAYMENT, requiresPrintTicket = false)
                    } else {
                        TransitionResult.Error("يجب طلب الدفع أولاً قبل التحضير في نظام الدفع المسبق")
                    }
                }
                OrderState.AWAITING_PAYMENT -> {
                    if (targetState == OrderState.PAID) {
                        TransitionResult.Success(OrderState.PAID, requiresPrintTicket = true)
                    } else {
                        TransitionResult.Error("الانتقال غير مسموح: يجب تأكيد استلام المبلغ أولاً")
                    }
                }
                OrderState.PAID -> {
                    if (targetState == OrderState.READY) {
                        TransitionResult.Success(OrderState.READY, requiresPrintTicket = false)
                    } else {
                        TransitionResult.Error("الانتقال غير مسموح من حالة الدفع إلا إلى جاهز للتقديم")
                    }
                }
                OrderState.READY -> {
                    if (targetState == OrderState.SERVED) {
                        TransitionResult.Success(OrderState.SERVED, requiresPrintTicket = false)
                    } else {
                        TransitionResult.Error("الطلب جاهز، الخطوة التالية هي التقديم للزبون")
                    }
                }
                else -> TransitionResult.Error("تحول غير قانوني في دورة الطلب")
            }
        } else {
            // paymentFirst == false (preparation first, payment after service)
            when (currentState) {
                OrderState.NEW -> {
                    if (targetState == OrderState.READY) {
                        TransitionResult.Success(OrderState.READY, requiresPrintTicket = true)
                    } else if (targetState == OrderState.AWAITING_PAYMENT) {
                        TransitionResult.Success(OrderState.AWAITING_PAYMENT, requiresPrintTicket = false)
                    } else {
                        TransitionResult.Error("الانتقال غير مسموح من جديد")
                    }
                }
                OrderState.READY -> {
                    if (targetState == OrderState.SERVED) {
                        TransitionResult.Success(OrderState.SERVED, requiresPrintTicket = false)
                    } else if (targetState == OrderState.AWAITING_PAYMENT) {
                        TransitionResult.Success(OrderState.AWAITING_PAYMENT, requiresPrintTicket = false)
                    } else {
                        TransitionResult.Error("الانتقال غير مسموح من جاهز")
                    }
                }
                OrderState.SERVED -> {
                    if (targetState == OrderState.AWAITING_PAYMENT) {
                        TransitionResult.Success(OrderState.AWAITING_PAYMENT, requiresPrintTicket = false)
                    } else if (targetState == OrderState.PAID) {
                        TransitionResult.Success(OrderState.PAID, requiresPrintTicket = false)
                    } else {
                        TransitionResult.Error("الانتقال غير مسموح من تم التقديم")
                    }
                }
                OrderState.AWAITING_PAYMENT -> {
                    if (targetState == OrderState.PAID) {
                        TransitionResult.Success(OrderState.PAID, requiresPrintTicket = false)
                    } else {
                        TransitionResult.Error("الانتقال غير مسموح: في انتظار الدفع")
                    }
                }
                else -> TransitionResult.Error("تحول غير قانوني في دورة الطلب")
            }
        }
    }

    /**
     * Checks if an order stuck in AWAITING_PAYMENT exceeds the alert threshold (default 5 min = 300,000 ms).
     */
    fun isStuckAwaitingPayment(
        currentState: OrderState,
        updatedAtMillis: Long,
        currentTimeMillis: Long = System.currentTimeMillis(),
        thresholdMillis: Long = 5 * 60 * 1000L
    ): Boolean {
        if (currentState != OrderState.AWAITING_PAYMENT) return false
        return (currentTimeMillis - updatedAtMillis) >= thresholdMillis
    }
}
