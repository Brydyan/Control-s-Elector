import { Component, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { FocusTrapDirective } from './focus-trap.directive';

@Component({
  standalone: true,
  imports: [CommonModule, FocusTrapDirective],
  template: `
    <button id="trigger" type="button" (click)="open()">Trigger</button>
    <div
      *ngIf="openModal"
      [appFocusTrap]="true"
      [escapeCallback]="close.bind(this)"
    >
      <button id="first" type="button">First</button>
      <input id="middle" type="text" />
      <button id="last" type="button">Last</button>
    </div>
  `,
  // Sin imports extra porque usamos ngIf nativo de Angular standalone.
})
class HostComponent {
  @ViewChild('trigger', { static: true })
  trigger!: ElementRef<HTMLElement>;
  openModal = false;
  close = vi.fn();
  open() {
    this.openModal = true;
  }
}

describe('FocusTrapDirective', () => {
  let fixture: ComponentFixture<HostComponent>;
  let component: HostComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('mounts with the trigger button visible and modal closed', () => {
    expect(fixture.nativeElement.querySelector('#trigger')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#first')).toBeNull();
  });

  it('renders the modal children after opening', () => {
    component.open();
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#first')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('#last')).toBeTruthy();
  });

  it('invokes escapeCallback when Escape is dispatched from inside the modal', () => {
    component.open();
    fixture.changeDetectorRef.markForCheck();
    fixture.detectChanges();

    const last = fixture.nativeElement.querySelector('#last') as HTMLElement;
    const escEvent = new KeyboardEvent('keydown', {
      key: 'Escape',
      bubbles: true,
      cancelable: true,
    });
    last.dispatchEvent(escEvent);

    expect(component.close).toHaveBeenCalled();
  });
});
