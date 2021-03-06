package com.johnlindquist.acejump.control

import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.johnlindquist.acejump.control.Handler.redoFind
import com.johnlindquist.acejump.control.Handler.reset
import com.johnlindquist.acejump.label.Tagger
import com.johnlindquist.acejump.search.getView
import com.johnlindquist.acejump.view.Model.editor
import com.johnlindquist.acejump.view.Model.viewBounds
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.event.AncestorEvent
import javax.swing.event.AncestorListener
import kotlin.system.measureTimeMillis

internal object Listener : FocusListener, AncestorListener,
  EditorColorsListener, VisibleAreaListener {
  fun enable() =
    synchronized(this) {
      editor.run {
        component.addFocusListener(Listener)
        component.addAncestorListener(Listener)
        scrollingModel.addVisibleAreaListener(Listener)
      }
    }

  fun disable() =
    synchronized(this) {
      editor.run {
        component.removeFocusListener(Listener)
        component.removeAncestorListener(Listener)
        scrollingModel.removeVisibleAreaListener(Listener)
      }
    }

  /**
   * This callback is very jittery. We need to delay repainting tags by a short
   * duration in order to prevent flashing tag syndrome.
   *
   * @see Trigger
   */
  override fun visibleAreaChanged(e: VisibleAreaEvent?) {
    val elapsed = measureTimeMillis { if (canTagsSurviveViewResize()) return }
    Trigger(withDelay = (750L - elapsed).coerceAtLeast(0L)) { redoFind() }
  }

  private fun canTagsSurviveViewResize() =
    editor.getView().run {
      if (first in viewBounds && last in viewBounds) return true
      else if (Tagger.full) return true
      else if (Tagger.regex) return false
      else !Tagger.hasMatchBetweenOldAndNewView(viewBounds, this)
    }

  override fun globalSchemeChange(scheme: EditorColorsScheme?) = redoFind()

  override fun ancestorAdded(event: AncestorEvent?) = reset()

  override fun ancestorMoved(event: AncestorEvent?) =
    if (canTagsSurviveViewResize()) Unit else reset()

  override fun ancestorRemoved(event: AncestorEvent?) = reset()

  override fun focusLost(e: FocusEvent?) = reset()

  override fun focusGained(e: FocusEvent?) = reset()
}