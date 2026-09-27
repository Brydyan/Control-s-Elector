import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { SkeletonComponent } from './skeleton.component';

@Component({
  standalone: true,
  imports: [SkeletonComponent],
  template: `
    <app-skeleton [loading]="loading" [loadingBoundary]="true">
      <span class="projected-loading">Loading records</span>
    </app-skeleton>
  `,
})
class LoadingBoundaryHostComponent {
  loading = true;
}

describe('SkeletonComponent', () => {
  let fixture: ComponentFixture<SkeletonComponent>;
  let component: SkeletonComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SkeletonComponent, LoadingBoundaryHostComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(SkeletonComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders a single rect skeleton by default', () => {
    expect(component.variant).toBe('rect');
    expect(component.lines).toBe(1);
    expect(fixture.nativeElement.querySelector('.skeleton--rect')).toBeTruthy();
  });

  it('renders a circle when variant=circle', () => {
    fixture.componentRef.setInput('variant', 'circle');
    fixture.componentRef.setInput('width', '48px');
    fixture.componentRef.setInput('height', '48px');
    fixture.detectChanges();
    const circle = fixture.nativeElement.querySelector('.skeleton--circle');
    expect(circle).toBeTruthy();
    expect(circle.style.width).toBe('48px');
  });

  it('renders N lines when variant=text and lines > 1', () => {
    fixture.componentRef.setInput('variant', 'text');
    fixture.componentRef.setInput('lines', 4);
    fixture.componentRef.setInput('height', '0.9em');
    fixture.detectChanges();
    const lines = fixture.nativeElement.querySelectorAll('.skeleton--text');
    expect(lines.length).toBe(4);
    expect(lines[lines.length - 1].style.width).toBe('60%');
  });

  it('clamps lines to at least 1', () => {
    component.lines = 0;
    expect(component.lineArray.length).toBe(1);
    component.lines = -5;
    expect(component.lineArray.length).toBe(1);
  });

  it('disables shimmer when animate is none', () => {
    fixture.componentRef.setInput('animate', 'none');
    fixture.detectChanges();
    const skeleton = fixture.nativeElement.querySelector('.skeleton');
    expect(skeleton.classList).toContain('skeleton--no-animation');
  });

  it('gates projected loading content on loading', async () => {
    const hostFixture = TestBed.createComponent(LoadingBoundaryHostComponent);
    hostFixture.detectChanges();
    expect(
      hostFixture.nativeElement.querySelector('.projected-loading'),
    ).toBeTruthy();

    hostFixture.componentInstance.loading = false;
    hostFixture.changeDetectorRef.markForCheck();
    hostFixture.detectChanges();
    expect(
      hostFixture.nativeElement.querySelector('.projected-loading'),
    ).toBeNull();
  });

  it('does not render loading content when loading is false', () => {
    fixture.componentRef.setInput('loading', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.skeleton')).toBeNull();
  });
});
