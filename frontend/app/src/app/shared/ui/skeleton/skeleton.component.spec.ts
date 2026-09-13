import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, beforeEach } from 'vitest';
import { SkeletonComponent } from './skeleton.component';

describe('SkeletonComponent', () => {
  let fixture: ComponentFixture<SkeletonComponent>;
  let component: SkeletonComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SkeletonComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(SkeletonComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders a single rect skeleton by default', () => {
    expect(component.variant).toBe('rect');
    expect(component.lines).toBe(1);
    const rect = fixture.nativeElement.querySelector(
      '.skeleton--rect',
    ) as HTMLElement;
    expect(rect).toBeTruthy();
  });

  it('renders a circle when variant=circle', () => {
    component.variant = 'circle';
    component.width = '48px';
    component.height = '48px';
    fixture.detectChanges();
    const circle = fixture.nativeElement.querySelector(
      '.skeleton--circle',
    ) as HTMLElement;
    expect(circle).toBeTruthy();
    expect(circle.style.width).toBe('48px');
  });

  it('renders N lines when variant=text and lines > 1', () => {
    component.variant = 'text';
    component.lines = 4;
    component.height = '0.9em';
    fixture.detectChanges();
    const lines = fixture.nativeElement.querySelectorAll('.skeleton--text');
    expect(lines.length).toBe(4);
    // La última línea tiene width 60% para parecer texto natural.
    const last = lines[lines.length - 1] as HTMLElement;
    expect(last.style.width).toBe('60%');
  });

  it('exposes lineArray of the requested length', () => {
    component.lines = 7;
    expect(component.lineArray.length).toBe(7);
  });

  it('clamps lines to at least 1', () => {
    component.lines = 0;
    expect(component.lineArray.length).toBe(1);
    component.lines = -5;
    expect(component.lineArray.length).toBe(1);
  });
});
