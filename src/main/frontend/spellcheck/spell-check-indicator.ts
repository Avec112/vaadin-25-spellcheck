/**
 * `<spell-check-indicator>`: the browser side of MyCustomTextArea /
 * MyCustomTextField spell checking.
 *
 * The Java side (SpellCheckSupport) puts this element into the suffix slot of a
 * `<vaadin-text-field>` or `<vaadin-text-area>` while spell checking is
 * enabled, and sets the `language` and `customWords` properties. The element:
 *
 *  - listens to the field's public `value-changed` event and checks the text
 *    600 ms after the last change (never on every keystroke);
 *  - runs the check in a shared Web Worker (spell-check-client.ts) and drops
 *    stale results (check-scheduler.ts);
 *  - draws red wavy underlines under misspelled words inside the field
 *    (spell-check-underlines.ts) and moves them along while the user types;
 *  - opens a small suggestion popover next to a misspelled word when the user
 *    clicks into it;
 *  - shows a compact button with the number of possible misspellings, which
 *    opens a `<vaadin-popover>` listing every misspelled word in context, with
 *    suggestions to replace it and an action to ignore it (also the keyboard
 *    route to all corrections);
 *  - when the application has a shared vocabulary (`canAddWords`), offers
 *    "Add to dictionary", which fires a `spell-check-add-word` event that the
 *    server answers with a dialog (SpellCheckSupport.java), and re-checks
 *    when the shared vocabulary changes.
 *
 * This element only uses public DOM of the Vaadin fields: the `value`
 * property, the `value-changed` event, the `readonly`/`disabled` attributes
 * and the slotted native input element (whose background shows the
 * underlines).
 *
 * Corrections are applied the same way Vaadin's own clear button changes the
 * value: by editing the native input and dispatching `input` and `change`
 * events. The field therefore syncs the new value to the server in every
 * ValueChangeMode, and Binder/validation run as for user input.
 */
import '@vaadin/button';
import '@vaadin/popover';
import type { Popover } from '@vaadin/popover';
import { css, html, LitElement, nothing, render, type PropertyValues } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { CheckScheduler } from './check-scheduler';
import { checkSpelling, isSupportedLanguage, onSharedVocabularyChange } from './spell-check-client';
import { SpellingUnderlines } from './spell-check-underlines';
import { applyReplacement, isAccepted, remapIssues, type SpellingIssue } from './spell-text';

type Status = 'empty' | 'checking' | 'ok' | 'issues' | 'error';

type FieldElement = HTMLElement & { value: string; readonly?: boolean; disabled?: boolean };

const LANGUAGE_NAMES: Record<string, string> = {
  'nb-NO': 'Norwegian Bokmål',
  'en-US': 'English (US)',
  'en-GB': 'English (UK)',
};

/** Characters of surrounding text shown around a misspelled word. */
const CONTEXT_LENGTH = 24;

@customElement('spell-check-indicator')
export class SpellCheckIndicator extends LitElement {
  /** BCP 47 code of a bundled dictionary, e.g. `nb-NO`. Set by the server. */
  @property() language = 'nb-NO';

  /** Component-specific accepted words, newline-separated. Set by the server. */
  @property({ attribute: false }) customWords = '';

  /** Whether "Add to dictionary" is offered. Set by the server. */
  @property({ attribute: false }) canAddWords = false;

  @state() private status: Status = 'empty';
  @state() private issues: SpellingIssue[] = [];
  @state() private errorMessage = '';
  /** True while the text has changed but has not been re-checked yet. */
  @state() private stale = false;

  /** The exact text the current `issues` refer to. */
  @state() private checkedText = '';
  /** The word whose suggestion popover is open next to it, if any. */
  @state() private wordIssue?: SpellingIssue;
  /** Words ignored by the user in this field (until the page is reloaded). */
  private readonly ignored = new Set<string>();
  private field?: FieldElement;
  private nativeInput?: HTMLInputElement | HTMLTextAreaElement;
  private nativeSpellcheck?: boolean;
  private suggestions?: Popover;
  private stateObserver?: MutationObserver;
  private focusPanelAfterUpdate = false;
  private underlines?: SpellingUnderlines;
  private removeVocabularyListener?: () => void;
  private wordPopover?: Popover;
  /** Invisible element at the clicked word, used to position `wordPopover`. */
  private wordAnchor?: HTMLElement;

  private readonly scheduler = new CheckScheduler<SpellingIssue[]>(
    (text) => checkSpelling(this.language, text, this.acceptedWords()),
    (issues, text) => this.showResult(issues, text),
    (error) => this.showError(error),
  );

  private readonly onValueChanged = () => {
    if (!this.active) {
      return;
    }
    const text = this.field!.value ?? '';
    // Keep underlines on words the edit did not touch until the next check.
    this.issues = remapIssues(this.checkedText, text, this.issues);
    this.checkedText = text;
    if (this.status === 'issues' && !this.issues.length) {
      this.status = 'checking'; // not "0 errors" before the text was checked
    }
    this.closeWordPopover();
    if (text.trim()) {
      this.stale = true;
      this.scheduler.schedule(text);
    } else {
      this.check(text);
    }
  };

  static override styles = css`
    :host {
      display: inline-flex;
      align-items: center;
      flex: none;
    }

    :host([hidden]) {
      display: none;
    }

    button {
      display: inline-flex;
      align-items: center;
      gap: 0.25em;
      margin: 0 0 0 0.25em;
      padding: 0 0.375em;
      height: 1.625em;
      border: none;
      border-radius: var(--lumo-border-radius-s, 4px);
      background: transparent;
      color: var(--lumo-secondary-text-color, #666);
      font: inherit;
      font-size: var(--lumo-font-size-xs, 0.75rem);
      line-height: 1;
      cursor: pointer;
      white-space: nowrap;
    }

    button:hover {
      background: var(--lumo-contrast-5pct, rgba(0, 0, 0, 0.05));
    }

    button:focus-visible {
      outline: 2px solid var(--lumo-primary-color-50pct, #1676f380);
      outline-offset: 1px;
    }

    .abc {
      font-weight: 600;
      letter-spacing: 0.02em;
    }

    .issues .abc {
      text-decoration: underline wavy var(--lumo-error-color, #e5222d);
      text-underline-offset: 0.2em;
      text-decoration-thickness: 1px;
    }

    .count {
      min-width: 1.4em;
      padding: 0.15em 0.35em;
      border-radius: 1em;
      background: var(--lumo-error-color, #e5222d);
      color: var(--lumo-error-contrast-color, #fff);
      font-weight: 600;
      text-align: center;
    }

    .ok-mark {
      color: var(--lumo-success-color, #1aa25a);
      font-weight: 700;
    }

    .stale,
    .checking {
      opacity: 0.6;
    }

    .error-color {
      display: none;
      color: var(--lumo-error-color, #e5222d);
    }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `;

  override connectedCallback() {
    super.connectedCallback();
    const parent = this.parentElement;
    if (!parent || !('value' in parent)) {
      console.warn('<spell-check-indicator> must be placed inside a Vaadin text field or text area');
      return;
    }
    this.field = parent as FieldElement;
    this.field.addEventListener('value-changed', this.onValueChanged);

    // Avoid two spell checkers (browser + ours) reporting different results.
    this.nativeInput = this.field.querySelector<HTMLInputElement | HTMLTextAreaElement>(':scope > input, :scope > textarea') ?? undefined;
    if (this.nativeInput) {
      this.nativeSpellcheck = this.nativeInput.spellcheck;
      this.nativeInput.spellcheck = false;
      this.nativeInput.addEventListener('click', this.onInputClick);
      this.underlines = new SpellingUnderlines(this.nativeInput, () => this.errorColor());
    }

    // Shared vocabulary changed (by any user): check again with it.
    this.removeVocabularyListener = onSharedVocabularyChange(() => this.recheck());

    // React to setReadOnly()/setEnabled() from the server.
    this.stateObserver = new MutationObserver(() => this.refresh());
    this.stateObserver.observe(this.field, { attributes: true, attributeFilter: ['readonly', 'disabled'] });
    this.refresh();
    // The button exists after the first render; also runs again after a
    // detach/attach cycle, because the popover is removed on detach.
    this.updateComplete.then(() => this.isConnected && !this.suggestions && this.createPopover());
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    // Detached or disabled from the server: stop everything, keep no results.
    this.scheduler.cancel();
    this.removeVocabularyListener?.();
    this.stateObserver?.disconnect();
    this.field?.removeEventListener('value-changed', this.onValueChanged);
    if (this.nativeInput && this.nativeSpellcheck !== undefined) {
      this.nativeInput.spellcheck = this.nativeSpellcheck;
    }
    this.nativeInput?.removeEventListener('click', this.onInputClick);
    this.underlines?.destroy();
    this.underlines = undefined;
    this.suggestions?.remove();
    this.suggestions = undefined;
    this.wordPopover?.remove();
    this.wordPopover = undefined;
    this.wordAnchor?.remove();
    this.wordAnchor = undefined;
    this.wordIssue = undefined;
    this.field = undefined;
    this.nativeInput = undefined;
    this.issues = [];
    this.status = 'empty';
  }

  protected override updated(changed: PropertyValues<this>) {
    // Ignore the initial values; connectedCallback already started a check.
    if ((changed.has('language') && changed.get('language') !== undefined) || (changed.has('customWords') && changed.get('customWords') !== undefined)) {
      this.refresh();
    }
    if (this.active && this.status === 'issues') {
      this.underlines?.show(this.checkedText, this.issues);
    } else {
      this.underlines?.clear();
    }
    this.suggestions?.requestContentUpdate();
    this.wordPopover?.requestContentUpdate();
  }

  private createPopover() {
    const popover = document.createElement('vaadin-popover');
    popover.target = this.renderRoot.querySelector('button')!;
    popover.position = 'bottom-end';
    popover.accessibleName = 'Spelling suggestions';
    popover.autofocus = true;
    popover.renderer = (root) => render(this.renderPanel(), root);
    popover.addEventListener('closed', () => this.restoreFocus());
    // Appended to <body> rather than inside the field, so that keyboard events
    // in the popover (e.g. Escape) are never handled by the field itself.
    document.body.append(popover);
    this.suggestions = popover;
  }

  /** Whether the field is editable, so spelling can be checked and fixed. */
  private get active(): boolean {
    return !!this.field && !this.field.disabled && !this.field.readonly;
  }

  /** Re-evaluates everything: active state, language and the current text. */
  private refresh() {
    this.scheduler.cancel();
    this.hidden = !this.active;
    this.issues = [];
    this.stale = false;
    if (!this.active) {
      if (this.suggestions) {
        this.suggestions.opened = false;
      }
      this.closeWordPopover();
      this.status = 'empty';
      return;
    }
    if (!isSupportedLanguage(this.language)) {
      this.showError(new Error(`Unsupported spell-check language: ${this.language}`));
      return;
    }
    // Results for another language or state are meaningless: start over.
    this.status = 'checking';
    this.check(this.field!.value ?? '');
  }

  /** Re-checks the current text without clearing the visible result first. */
  private recheck() {
    if (this.active && isSupportedLanguage(this.language)) {
      this.check(this.field!.value ?? '');
    }
  }

  /** Checks immediately, keeping the previous result visible (dimmed) meanwhile. */
  private check(text: string) {
    if (!text.trim()) {
      // Nothing to check: do not load a dictionary for an empty field.
      this.scheduler.cancel();
      this.showResult([], text);
      return;
    }
    if (this.status === 'empty' || this.status === 'error') {
      this.status = 'checking';
    } else {
      this.stale = true;
    }
    this.scheduler.checkNow(text);
  }

  private acceptedWords(): string[] {
    return [...this.customWords.split('\n').filter(Boolean), ...this.ignored];
  }

  private showResult(issues: SpellingIssue[], text: string) {
    this.checkedText = text;
    this.issues = issues;
    this.stale = text !== (this.field?.value ?? '');
    this.status = issues.length ? 'issues' : text.trim() ? 'ok' : 'empty';
    this.errorMessage = '';
    if (this.focusPanelAfterUpdate) {
      this.focusPanelAfterUpdate = false;
      this.focusPanel();
    }
  }

  /**
   * After a correction the list is re-rendered and the focused button may
   * disappear. Keep keyboard focus inside the popover instead of losing it.
   */
  private focusPanel() {
    requestAnimationFrame(() => this.suggestions?.querySelector('spell-check-panel')?.focusFirst());
  }

  private showError(error: unknown) {
    this.issues = [];
    this.stale = false;
    this.status = 'error';
    this.errorMessage = error instanceof Error ? error.message : String(error);
    console.error('Spell checking failed:', error);
  }

  /** Opens the suggestion popover next to a misspelled word the user clicked into. */
  private readonly onInputClick = () => {
    const input = this.nativeInput!;
    const caret = input.selectionStart;
    const issue =
      this.active && caret === input.selectionEnd && this.checkedText === input.value
        ? this.issues.find((i) => i.start <= caret! && caret! <= i.end)
        : undefined;
    if (issue) {
      this.openWordPopover(issue);
    } else {
      this.closeWordPopover();
    }
  };

  private openWordPopover(issue: SpellingIssue) {
    const [rect] = this.underlines?.wordRects(issue) ?? [];
    if (!rect) {
      return;
    }
    if (!this.wordPopover) {
      this.wordAnchor = document.createElement('div');
      this.wordAnchor.style.cssText = 'position: fixed; height: 0; pointer-events: none; visibility: hidden;';
      const popover = document.createElement('vaadin-popover');
      popover.target = this.wordAnchor;
      popover.trigger = []; // opened programmatically
      popover.position = 'bottom-start';
      // Focus stays in the field, so the user can simply continue typing.
      popover.autofocus = false;
      popover.renderer = (root) => render(this.renderPanel(true), root);
      popover.addEventListener('closed', () => (this.wordIssue = undefined));
      document.body.append(this.wordAnchor, popover);
      this.wordPopover = popover;
    }
    Object.assign(this.wordAnchor!.style, { left: `${rect.left}px`, top: `${rect.bottom}px`, width: `${rect.width}px` });
    this.wordIssue = issue;
    this.wordPopover.accessibleName = `Spelling suggestions for ${issue.word}`;
    this.wordPopover.opened = true;
  }

  private closeWordPopover() {
    if (this.wordPopover?.opened) {
      this.wordPopover.opened = false;
    }
    this.wordIssue = undefined;
  }

  /** The theme's error color, resolved by the browser (works with dark mode). */
  private errorColor(): string {
    const probe = this.renderRoot.querySelector('.error-color');
    return (probe && getComputedStyle(probe).color) || '#e5222d';
  }

  /** Asks the server to open its "Add to dictionary" dialog for the word. */
  private requestAddWord(issue: SpellingIssue) {
    this.closeWordPopover();
    if (this.suggestions) {
      this.suggestions.opened = false;
    }
    this.dispatchEvent(new CustomEvent('spell-check-add-word', { detail: { word: issue.word } }));
  }

  /** Called from a popover after the user picked a suggestion or ignored a word. */
  private afterWordAction(fromWordPopover: boolean) {
    if (fromWordPopover) {
      this.closeWordPopover();
      this.nativeInput?.focus();
    } else {
      this.focusPanelAfterUpdate = true;
    }
  }

  private replace(issue: SpellingIssue, replacement: string, fromWordPopover = false) {
    const input = this.nativeInput;
    const current = this.field?.value ?? '';
    // Only replace if the word is still exactly where it was found.
    if (!input || current !== this.checkedText || applyReplacement(current, issue, replacement) === null) {
      this.check(current);
      return;
    }
    // Keep the user's caret/selection where it was, shifted by the length
    // change. (setRangeText's own 'preserve' mode moves a caret that sits right
    // after the word to the start of the word, so it is mapped explicitly.)
    const delta = replacement.length - (issue.end - issue.start);
    const map = (pos: number) => (pos >= issue.end ? pos + delta : pos > issue.start ? issue.start + replacement.length : pos);
    const [selectionStart, selectionEnd] = [map(input.selectionStart ?? 0), map(input.selectionEnd ?? 0)];
    input.setRangeText(replacement, issue.start, issue.end);
    input.setSelectionRange(selectionStart, selectionEnd);
    // Same events as Vaadin's clear button: updates `value`, runs validation
    // and syncs to the server regardless of ValueChangeMode.
    input.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
    this.afterWordAction(fromWordPopover);
    this.check(this.field!.value);
  }

  private ignore(issue: SpellingIssue, fromWordPopover = false) {
    this.ignored.add(issue.word);
    this.issues = this.issues.filter((other) => !isAccepted(other.word, this.ignored));
    this.status = this.issues.length ? 'issues' : 'ok';
    if (fromWordPopover) {
      this.afterWordAction(true);
    } else {
      this.focusPanel();
    }
  }

  private restoreFocus() {
    // When the popover closes with focus inside it (Escape, or no element to
    // return to), continue editing in the field instead of on the button.
    const active = document.activeElement;
    if (this.active && (active === document.body || active === this || this.suggestions?.contains(active))) {
      this.nativeInput?.focus();
    }
  }

  private label(): string {
    switch (this.status) {
      case 'checking':
        return 'Checking spelling';
      case 'issues':
        return `${this.issues.length} possible spelling ${this.issues.length === 1 ? 'error' : 'errors'}`;
      case 'ok':
        return 'No spelling errors found';
      case 'error':
        return `Spell checking unavailable: ${this.errorMessage}`;
      default:
        return 'Spell checking';
    }
  }

  override render() {
    const label = this.label();
    // The button names its action; the live region only announces the result.
    const buttonLabel = this.status === 'issues' ? `${label}. Show suggestions` : label;
    const count = this.issues.length > 99 ? '99+' : String(this.issues.length);
    return html`
      <button
        type="button"
        class="${this.status} ${this.stale ? 'stale' : ''}"
        aria-label="${buttonLabel}"
        aria-haspopup="dialog"
        title="${buttonLabel}"
      >
        <span class="abc" aria-hidden="true">abc</span>
        ${this.status === 'issues' ? html`<span class="count" aria-hidden="true">${count}</span>` : nothing}
        ${this.status === 'ok' ? html`<span class="ok-mark" aria-hidden="true">✓</span>` : nothing}
        ${this.status === 'error' ? html`<span aria-hidden="true">!</span>` : nothing}
      </button>
      <span class="sr-only" role="status">${this.status === 'checking' ? '' : label}</span>
      <span class="error-color" aria-hidden="true"></span>
    `;
  }

  /** @param forWord renders the compact panel for `wordIssue` only */
  private renderPanel(forWord = false) {
    const issues = forWord ? (this.wordIssue ? [this.wordIssue] : []) : this.issues;
    return html`<spell-check-panel
      .compact=${forWord}
      .issues=${issues}
      .text=${this.checkedText}
      .status=${this.status}
      .languageName=${LANGUAGE_NAMES[this.language] ?? this.language}
      .errorMessage=${this.errorMessage}
      .onReplace=${(issue: SpellingIssue, word: string) => this.replace(issue, word, forWord)}
      .onIgnore=${(issue: SpellingIssue) => this.ignore(issue, forWord)}
      .onAddWord=${this.canAddWords ? (issue: SpellingIssue) => this.requestAddWord(issue) : undefined}
    ></spell-check-panel>`;
  }
}

/** Popover content: the list of misspellings with their suggestions. */
@customElement('spell-check-panel')
export class SpellCheckPanel extends LitElement {
  /** Compact variant next to a word: no header and no context line. */
  @property({ attribute: false }) compact = false;
  @property({ attribute: false }) issues: SpellingIssue[] = [];
  @property({ attribute: false }) text = '';
  @property({ attribute: false }) status: Status = 'empty';
  @property({ attribute: false }) languageName = '';
  @property({ attribute: false }) errorMessage = '';
  @property({ attribute: false }) onReplace: (issue: SpellingIssue, word: string) => void = () => {};
  @property({ attribute: false }) onIgnore: (issue: SpellingIssue) => void = () => {};
  /** When set, an "Add to dictionary" action is shown. */
  @property({ attribute: false }) onAddWord?: (issue: SpellingIssue) => void;

  static override styles = css`
    :host {
      display: block;
      width: min(22rem, 80vw);
    }

    :host([compact]) {
      width: auto;
      max-width: min(22rem, 80vw);
      max-height: min(24rem, 60vh);
      overflow: auto;
      font-size: var(--lumo-font-size-s, 0.875rem);
    }

    header {
      display: flex;
      justify-content: space-between;
      gap: 1em;
      padding-bottom: 0.5em;
      color: var(--lumo-secondary-text-color, #666);
      font-size: var(--lumo-font-size-xs, 0.75rem);
    }

    ul {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    li {
      padding: 0.5em 0;
      border-top: 1px solid var(--lumo-contrast-10pct, rgba(0, 0, 0, 0.1));
    }

    .context {
      color: var(--lumo-secondary-text-color, #666);
      overflow-wrap: anywhere;
    }

    .word {
      color: var(--lumo-body-text-color, inherit);
      font-weight: 600;
      text-decoration: underline wavy var(--lumo-error-color, #e5222d);
      text-underline-offset: 0.2em;
      text-decoration-thickness: 1px;
    }

    .actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.25em;
      margin-top: 0.375em;
    }

    .secondary {
      display: flex;
      margin-left: auto;
    }

    .none,
    .message {
      margin: 0;
      color: var(--lumo-secondary-text-color, #666);
    }

    .ok {
      color: var(--lumo-success-text-color, #1aa25a);
    }
  `;

  override connectedCallback() {
    super.connectedCallback();
    // Focus fallback when no button is left in the panel.
    this.tabIndex = -1;
  }

  /** Focuses the first action, or the panel itself if there is none. */
  focusFirst() {
    const button = this.renderRoot.querySelector<HTMLElement>('vaadin-button');
    (button ?? this).focus();
  }

  override updated() {
    this.toggleAttribute('compact', this.compact);
  }

  override render() {
    if (this.compact) {
      return html`<div class="actions">${this.renderActions(this.issues[0])}</div>`;
    }
    return html`
      <header>
        <strong>Spelling</strong>
        <span>${this.languageName}</span>
      </header>
      ${this.renderBody()}
    `;
  }

  private renderBody() {
    switch (this.status) {
      case 'checking':
        return html`<p class="message">Checking spelling…</p>`;
      case 'error':
        return html`<p class="message">Spell checking is unavailable. ${this.errorMessage}</p>`;
      case 'empty':
        return html`<p class="message">Type some text to check its spelling.</p>`;
      case 'ok':
        return html`<p class="message ok">✓ No spelling errors found.</p>`;
      default:
        return html`<ul>
          ${this.issues.map((issue) => this.renderIssue(issue))}
        </ul>`;
    }
  }

  private renderIssue(issue: SpellingIssue) {
    const before = this.text.slice(Math.max(0, issue.start - CONTEXT_LENGTH), issue.start);
    const after = this.text.slice(issue.end, issue.end + CONTEXT_LENGTH);
    const trim = (s: string) => s.replace(/\s+/g, ' ');
    return html`<li>
      <div class="context">
        ${issue.start > CONTEXT_LENGTH ? '…' : ''}${trim(before)}<span class="word">${issue.word}</span>${trim(after)}${issue.end + CONTEXT_LENGTH < this.text.length ? '…' : ''}
      </div>
      <div class="actions">${this.renderActions(issue)}</div>
    </li>`;
  }

  private renderActions(issue: SpellingIssue | undefined) {
    if (!issue) {
      return nothing;
    }
    return html`
        ${issue.suggestions.length
          ? issue.suggestions.map(
              (suggestion) => html`<vaadin-button
                theme="small"
                aria-label="Replace ${issue.word} with ${suggestion}"
                @click=${() => this.onReplace(issue, suggestion)}
                >${suggestion}</vaadin-button
              >`,
            )
          : html`<span class="none">No suggestions</span>`}
        <span class="secondary">
          <vaadin-button
            theme="small tertiary"
            aria-label="Ignore ${issue.word}"
            @click=${() => this.onIgnore(issue)}
            >Ignore</vaadin-button
          >
          ${this.onAddWord
            ? html`<vaadin-button
                theme="small tertiary"
                aria-label="Add ${issue.word} to the dictionary"
                @click=${() => this.onAddWord!(issue)}
                >Add to dictionary</vaadin-button
              >`
            : nothing}
        </span>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'spell-check-indicator': SpellCheckIndicator;
    'spell-check-panel': SpellCheckPanel;
  }
}
