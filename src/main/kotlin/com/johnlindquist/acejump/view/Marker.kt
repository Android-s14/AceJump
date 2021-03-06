package com.johnlindquist.acejump.view

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.johnlindquist.acejump.config.AceConfig.Companion.settings
import com.johnlindquist.acejump.label.Tagger.regex
import com.johnlindquist.acejump.search.*
import com.johnlindquist.acejump.view.Marker.Alignment.*
import com.johnlindquist.acejump.view.Model.arcD
import com.johnlindquist.acejump.view.Model.editor
import com.johnlindquist.acejump.view.Model.fontHeight
import com.johnlindquist.acejump.view.Model.fontWidth
import com.johnlindquist.acejump.view.Model.rectHeight
import com.johnlindquist.acejump.view.Model.rectVOffset
import java.awt.AlphaComposite.SRC_OVER
import java.awt.AlphaComposite.getInstance
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints.KEY_ANTIALIASING
import java.awt.RenderingHints.VALUE_ANTIALIAS_ON
import com.johnlindquist.acejump.view.Model.editorText as text

/**
 * All functionality related to tag highlighting (ie. the visual overlay which
 * AceJump paints when displaying search results). Tags are "captioned" with two
 * or fewer characters. To select a tag, a user will type the tag's assigned
 * caption, which will move the cursor to a known index in the document.
 */

class Marker(val query: String, val tag: String?, val index: Int)
  : CustomHighlighterRenderer {
  private var srcPoint = editor.getPointRelative(index, editor.contentComponent)
  private var queryLength = query.length
  private var trueOffset = query.length - 1
  private val searchWidth = queryLength * fontWidth

  // TODO: Clean up this mess.
  init {
    var i = 1
    while (i < query.length && index + i < text.length &&
      query[i].toLowerCase() == text[index + i].toLowerCase()) i++

    trueOffset = i - 1
    queryLength = i
  }

  private val tgIdx = index + trueOffset
  private val start = editor.getPoint(index)
  private val startY = start.y + rectVOffset
  private var tagPoint = editor.getPointRelative(tgIdx, editor.contentComponent)
  private var yPosition = tagPoint.y + rectVOffset
  private var alignment = RIGHT

  enum class Alignment { /*TOP, BOTTOM,*/ LEFT, RIGHT, NONE }

  fun paintMe(graphics2D: Graphics2D) = graphics2D.run {
    setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON)

    if (!Finder.skim)
      tag?.alignTag(Canvas)
        ?.apply { Canvas.registerTag(this, tag) }
        ?.let { highlightTag(it); drawTagForeground(it) }
  }

  override fun paint(editor: Editor, highlight: RangeHighlighter, g: Graphics) =
    (g as Graphics2D).run {
      setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON)

      color = settings.textHighlightColor
      fillRoundRect(start.x, startY, searchWidth, rectHeight, arcD, arcD)

      if (Jumper.targetModeEnabled) surroundTargetWord()
    }

  private fun Graphics2D.surroundTargetWord() {
    color = settings.targetModeColor
    val (wordStart, wordEnd) = text.wordBounds(index)

    val xPosition = editor.getPoint(wordStart).x
    val wordWidth = (wordEnd - wordStart) * fontWidth

    if (text[index].isLetterOrDigit())
      drawRoundRect(xPosition, startY, wordWidth, rectHeight, arcD, arcD)
  }

  private fun Graphics2D.drawTagForeground(tagPosition: Point?) {
    font = Model.font
    color = settings.tagForegroundColor
    composite = getInstance(SRC_OVER, 1.toFloat())

    drawString(tag!!.toUpperCase(), tagPosition!!.x, tagPosition.y + fontHeight)
  }

  private fun String.alignTag(canvas: Canvas): Point {
    val x = tagPoint.x + fontWidth
//    val top = Point(x - fontWidth, y - fontHeight)
//    val bottom = Point(x - fontWidth, y + fontHeight)
    val left = Point(srcPoint.x - fontWidth * length, yPosition)
    val right = Point(x, yPosition)

    val nextCharIsWhiteSpace = text.length <= index + 1 ||
      text[index + 1].isWhitespace()

    val canAlignRight = canvas.isFree(right)
    val isFirstCharacterOfLine = editor.isFirstCharacterOfLine(index)
    val canAlignLeft = !isFirstCharacterOfLine && canvas.isFree(left)

    alignment = when {
      nextCharIsWhiteSpace -> RIGHT
      isFirstCharacterOfLine -> RIGHT
      canAlignLeft -> LEFT
      canAlignRight -> RIGHT
      else -> NONE
    }

    return when (alignment) {
//      TOP -> top
      LEFT -> left
      RIGHT -> right
//      BOTTOM -> bottom
      NONE -> Point(0, 0)
    }
  }

  private fun Graphics2D.highlightTag(point: Point?) {
    if (query.isEmpty() || alignment == NONE) return

    var tagX = point?.x
    val lastQueryChar = query.last()
    var tagWidth = tag?.length?.times(fontWidth) ?: 0
    val charIndex = index + query.length - 1
    val beforeEnd = charIndex < text.length
    val textChar = if (beforeEnd) text[charIndex].toLowerCase() else 0.toChar()

    //TODO use the IDE highlighter
    fun highlightRegex() {
      if (!regex) return
      color = settings.textHighlightColor
      composite = getInstance(SRC_OVER, 0.40.toFloat())

      if (alignment == RIGHT)
        fillRoundRect(tagX!! - fontWidth, yPosition, fontWidth, rectHeight, arcD, arcD)
      else
        fillRoundRect(tagX!! + tagWidth, yPosition, fontWidth, rectHeight, arcD, arcD)
    }

    fun highlightFirst() {
      composite = getInstance(SRC_OVER, 0.40.toFloat())
      color = settings.textHighlightColor

      if (tag != null && lastQueryChar == tag.first() && lastQueryChar != textChar) {
        fillRoundRect(tagX!!, yPosition, fontWidth, rectHeight, arcD, arcD)
        tagX += fontWidth
        tagWidth -= fontWidth
      }
    }

    fun highlightLast() {
      color = settings.tagBackgroundColor
      if (alignment != RIGHT || text.hasSpaceRight(index) || regex)
        composite = getInstance(SRC_OVER, 1.toFloat())

      fillRoundRect(tagX!!, yPosition, tagWidth, rectHeight, arcD, arcD)
    }

    highlightRegex()
    highlightFirst()
    highlightLast()
  }
}