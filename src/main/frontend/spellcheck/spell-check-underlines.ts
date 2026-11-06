/**
 * Draws red wavy underlines under misspelled words inside a native
 * `<input>` or `<textarea>`.
 *
 * Form controls cannot style parts of their own text, so the underlines are
 * drawn as an SVG *background image of the native element itself*:
 *
 *  1. An invisible "mirror" element gets the same font, padding, border
 *     widths, width and wrapping rules as the field (read with
 *     getComputedStyle), and the same text with each misspelled word wrapped
 *     in a span. The spans' rectangles are exactly where the words are drawn
 *     in the field.
 *  2. Wavy lines at those positions are rendered into an SVG, which becomes
 *     the field's `background-image`. The text is painted on top of its own
 *     background, so the result looks like a normal underline.
 *
 * Using the background (instead of an overlay element) means the underlines
 * move with the text automatically, wherever Vaadin scrolls or resizes the
 * field. Vaadin's text area grows with its content and scrolls inside its
 * shadow DOM, which an overlay could not follow. When the native element
 * scrolls its own content (the text field's horizontal scroll), the
 * background is offset by scrollLeft/scrollTop.
 *
 * The native element keeps all input handling: the underlines are paint
 * only, so typing, selection, IME, undo and Vaadin's value handling are not
 * affected. Only `background-*` inline styles are touched, and they are
 * restored by destroy().
 */
import type { Token } from './spell-text';

type NativeField = HTMLInputElement | HTMLTextAreaElement;

/** Computed styles that affect where text is laid out. */
const LAYOUT_PROPERTIES = [
  'font-family',
  'font-size',
  'font-weight',
  'font-style',
  'font-variant',
  'font-stretch',
  'font-kerning',
  'font-feature-settings',
  'font-variation-settings',
  'letter-spacing',
  'word-spacing',
  'line-height',
  'text-transform',
  'text-indent',
  'text-align',
  'tab-size',
  'direction',
  'white-space',
  'overflow-wrap',
  'word-break',
  'padding-top',
  'padding-right',
  'padding-bottom',
  'padding-left',
  'border-top-width',
  'border-right-width',
  'border-bottom-width',
  'border-left-width',
] as const;

const BACKGROUND_PROPERTIES = [
  'background-image',
  'background-repeat',
  'background-position',
  'background-size',
  'background-origin',
  'background-clip',
] as const;

/**
 * Wave baseline relative to the bottom of the word's box (which already
 * includes descenders), and the wave height. Slightly below the box, so the
 * wave does not touch letters like g, j and p.
 */
const WAVE_GAP = 1.5;
const WAVE_HEIGHT = 1.5;

export class SpellingUnderlines {
  private readonly input: NativeField;
  private readonly color: () => string;
  private readonly savedStyles = new Map<string, [string, string]>();
  private readonly resizeObserver: ResizeObserver;
  private text = '';
  private words: readonly Token[] = [];
  /** Word rectangles from the last render, relative to the field's border box. */
  private drawnRects = new Map<string, DOMRect[]>();
  private frame = 0;

  private readonly onScroll = () => this.updatePosition();

  /**
   * @param input the native input or textarea
   * @param color returns the underline color (read on every render, so theme
   *   changes are picked up)
   */
  constructor(input: NativeField, color: () => string) {
    this.input = input;
    this.color = color;
    for (const property of BACKGROUND_PROPERTIES) {
      this.savedStyles.set(property, [input.style.getPropertyValue(property), input.style.getPropertyPriority(property)]);
    }
    this.resizeObserver = new ResizeObserver(() => this.scheduleRender());
    this.resizeObserver.observe(input);
    input.addEventListener('scroll', this.onScroll);
    // The browser does not always fire `scroll` when the caret scrolls a
    // single-line input, so also follow caret and focus changes.
    for (const type of ['input', 'keyup', 'click', 'focus', 'blur', 'select']) {
      input.addEventListener(type, this.onScroll);
    }
    // Text metrics change once web fonts have loaded.
    document.fonts?.ready.then(() => this.scheduleRender());
  }

  /**
   * Underlines `words` (sorted, non-overlapping offsets into `text`). Nothing
   * is drawn unless `text` is exactly the field's current value, so stale
   * offsets can never produce misplaced underlines.
   */
  show(text: string, words: readonly Token[]) {
    this.text = text;
    this.words = words;
    this.scheduleRender();
  }

  clear() {
    this.show('', []);
  }

  /** The word's rectangles in viewport coordinates, as currently drawn. */
  wordRects(word: Token): DOMRect[] {
    const box = this.input.getBoundingClientRect();
    return (this.drawnRects.get(key(word)) ?? []).map(
      (r) => new DOMRect(box.left + r.left - this.input.scrollLeft, box.top + r.top - this.input.scrollTop, r.width, r.height),
    );
  }

  destroy() {
    cancelAnimationFrame(this.frame);
    this.resizeObserver.disconnect();
    for (const type of ['scroll', 'input', 'keyup', 'click', 'focus', 'blur', 'select']) {
      this.input.removeEventListener(type, this.onScroll);
    }
    for (const [property, [value, priority]] of this.savedStyles) {
      this.input.style.setProperty(property, value, priority);
    }
  }

  private scheduleRender() {
    if (!this.frame) {
      this.frame = requestAnimationFrame(() => {
        this.frame = 0;
        this.render();
      });
    }
  }

  private render() {
    const style = this.input.style;
    if (!this.words.length || this.text !== this.input.value || !this.input.isConnected || !this.input.clientWidth) {
      style.removeProperty('background-image');
      this.drawnRects.clear();
      return;
    }
    const rects = this.measure();
    this.drawnRects = new Map(this.words.map((word, i) => [key(word), rects[i]]));

    const all = rects.flat();
    const width = Math.ceil(Math.max(this.input.offsetWidth, ...all.map((r) => r.right + 4)));
    const height = Math.ceil(Math.max(this.input.offsetHeight, ...all.map((r) => r.bottom + 4)));
    // Bottom of the area where the background is visible (padding box,
    // including content scrolled inside the element). The last line's wave
    // is lifted to stay inside it; Firefox sizes text areas tightly.
    const limit = this.input.clientTop + this.input.scrollHeight;
    const path = all.map((rect) => wavePath(rect, limit)).join('');
    const svg =
      `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">` +
      `<path d="${path}" fill="none" stroke="${this.color()}" stroke-width="1"/></svg>`;

    style.setProperty('background-image', `url("data:image/svg+xml,${encodeURIComponent(svg)}")`);
    style.setProperty('background-repeat', 'no-repeat');
    style.setProperty('background-size', `${width}px ${height}px`);
    style.setProperty('background-origin', 'border-box');
    style.setProperty('background-clip', 'padding-box');
    this.updatePosition();
  }

  /** Follows content scrolled inside the native element itself. */
  private updatePosition() {
    if (this.words.length) {
      this.input.style.setProperty('background-position', `${-this.input.scrollLeft}px ${-this.input.scrollTop}px`);
    }
  }

  /** Returns the rectangles of each word, relative to the field's border box. */
  private measure(): DOMRect[][] {
    const input = this.input;
    const computed = getComputedStyle(input);
    const singleLine = input instanceof HTMLInputElement;
    const px = (property: string) => parseFloat(computed.getPropertyValue(property)) || 0;

    const mirror = document.createElement('div');
    for (const property of LAYOUT_PROPERTIES) {
      mirror.style.setProperty(property, computed.getPropertyValue(property));
    }
    Object.assign(mirror.style, {
      position: 'absolute',
      top: '0',
      left: '-100000px',
      visibility: 'hidden',
      pointerEvents: 'none',
      margin: '0',
      boxSizing: 'content-box',
      borderStyle: 'solid',
      borderColor: 'transparent',
      overflow: 'hidden',
      // clientWidth/Height exclude borders and scrollbars, include padding.
      width: `${input.clientWidth - px('padding-left') - px('padding-right')}px`,
    });

    // A single-line input never wraps and centers its line vertically.
    let content: HTMLElement = mirror;
    if (singleLine) {
      Object.assign(mirror.style, {
        display: 'flex',
        alignItems: 'center',
        height: `${input.clientHeight - px('padding-top') - px('padding-bottom')}px`,
      });
      content = document.createElement('div');
      content.style.whiteSpace = 'pre';
      mirror.append(content);
    }

    const spans: HTMLSpanElement[] = [];
    let offset = 0;
    for (const word of this.words) {
      const span = document.createElement('span');
      span.textContent = this.text.slice(word.start, word.end);
      content.append(this.text.slice(offset, word.start), span);
      spans.push(span);
      offset = word.end;
    }
    content.append(this.text.slice(offset));

    document.body.append(mirror);
    const origin = mirror.getBoundingClientRect();
    const rects = spans.map((span) =>
      Array.from(span.getClientRects(), (r) => new DOMRect(r.left - origin.left, r.top - origin.top, r.width, r.height)),
    );
    mirror.remove();
    return rects;
  }
}

function key(word: Token): string {
  return `${word.start}:${word.end}`;
}

/** A wavy line under the rectangle, built from alternating quadratic curves. */
function wavePath(rect: DOMRect, limit: number): string {
  const y = Math.min(rect.bottom + WAVE_GAP, limit - WAVE_HEIGHT).toFixed(1);
  const waves = Math.max(2, Math.round(rect.width / 2));
  return `M${rect.left.toFixed(1)} ${y}q1 ${-WAVE_HEIGHT} 2 0${'t2 0'.repeat(waves - 1)}`;
}
