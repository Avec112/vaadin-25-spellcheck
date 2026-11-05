/**
 * Debounces spell checks and guarantees that only the result of the most
 * recent request is delivered.
 *
 * Every scheduled or started check increments a version number. A result is
 * delivered only if its version is still the latest when it arrives, so a slow
 * check for old text can never overwrite the result for newer text, and
 * `cancel()` makes every in-flight check obsolete (used on detach, disable and
 * language change).
 */
export class CheckScheduler<T> {
  private readonly run: (text: string) => Promise<T>;
  private readonly onResult: (result: T, text: string) => void;
  private readonly onError: (error: unknown) => void;
  private readonly delay: number;
  private version = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;

  constructor(
    run: (text: string) => Promise<T>,
    onResult: (result: T, text: string) => void,
    onError: (error: unknown) => void,
    delay = 600,
  ) {
    this.run = run;
    this.onResult = onResult;
    this.onError = onError;
    this.delay = delay;
  }

  /** Checks the text after the debounce delay, replacing any pending check. */
  schedule(text: string): void {
    this.cancel();
    this.timer = setTimeout(() => this.checkNow(text), this.delay);
  }

  /** Checks the text immediately, replacing any pending check. */
  checkNow(text: string): void {
    this.cancel();
    const version = this.version;
    this.run(text).then(
      (result) => version === this.version && this.onResult(result, text),
      (error) => version === this.version && this.onError(error),
    );
  }

  /** Drops the pending timer and makes all in-flight checks obsolete. */
  cancel(): void {
    clearTimeout(this.timer);
    this.timer = undefined;
    this.version++;
  }

  /** Whether a debounced check is waiting to run. */
  get isPending(): boolean {
    return this.timer !== undefined;
  }
}
