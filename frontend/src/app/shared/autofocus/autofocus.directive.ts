import { AfterViewInit, Directive, ElementRef, Input } from '@angular/core';

/**
 * Focuses the host element once it renders. Angular ignores the native `autofocus` attribute for
 * dynamically-created views (inline editors, *ngIf blocks), so use `appAutofocus` instead.
 * Pass a falsy value to disable (e.g. `[appAutofocus]="isEditing"`).
 */
@Directive({
  selector: '[appAutofocus]',
  standalone: true,
})
export class AutofocusDirective implements AfterViewInit {
  @Input('appAutofocus') enabled: boolean | '' = true;

  constructor(private el: ElementRef<HTMLElement>) {}

  ngAfterViewInit(): void {
    if (this.enabled === false) return;
    // Defer so the element is attached and any overlay/animation has settled.
    setTimeout(() => this.el.nativeElement.focus());
  }
}
