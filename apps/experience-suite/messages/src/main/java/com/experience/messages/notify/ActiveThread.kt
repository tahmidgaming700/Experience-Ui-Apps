package com.experience.messages.notify

/** The thread currently on screen (resumed), so incoming messages there do not notify. */
object ActiveThread {
    @Volatile var threadId: Long? = null
}
