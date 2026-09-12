import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ButtonComponent } from './button.component';

describe('ButtonComponent', () => {
  let component: ButtonComponent;
  let fixture: ComponentFixture<ButtonComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ButtonComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ButtonComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should emit clicked event when not disabled or loading', () => {
    spyOn(component.clicked, 'emit');
    const event = new MouseEvent('click');
    component.onClick(event);
    expect(component.clicked.emit).toHaveBeenCalledWith(event);
  });

  it('should not emit clicked event when disabled', () => {
    spyOn(component.clicked, 'emit');
    component.disabled = true;
    const event = new MouseEvent('click');
    component.onClick(event);
    expect(component.clicked.emit).not.toHaveBeenCalled();
  });

  it('should not emit clicked event when loading', () => {
    spyOn(component.clicked, 'emit');
    component.loading = true;
    const event = new MouseEvent('click');
    component.onClick(event);
    expect(component.clicked.emit).not.toHaveBeenCalled();
  });
});
