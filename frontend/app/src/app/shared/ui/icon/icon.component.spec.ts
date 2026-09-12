import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IconComponent } from './icon.component';

describe('IconComponent', () => {
  let component: IconComponent;
  let fixture: ComponentFixture<IconComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [IconComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(IconComponent);
    component = fixture.componentInstance;
    component.name = 'home-outline';
    fixture.detectChanges();
  });

  it('should create and render ion-icon with name', () => {
    expect(component).toBeTruthy();
    const ionIcon = fixture.nativeElement.querySelector('ion-icon');
    expect(ionIcon).toBeTruthy();
  });

  it('should have aria-hidden by default', () => {
    const ionIcon = fixture.nativeElement.querySelector('ion-icon');
    expect(ionIcon.getAttribute('aria-hidden')).toBe('true');
  });
});
