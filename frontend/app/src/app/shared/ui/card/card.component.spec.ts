import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CardComponent } from './card.component';

describe('CardComponent', () => {
  let component: CardComponent;
  let fixture: ComponentFixture<CardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(CardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display headerTitle when provided', () => {
    fixture.componentRef.setInput('headerTitle', 'Card Title');
    fixture.detectChanges();
    const titleEl = fixture.nativeElement.querySelector('.app-card__title');
    expect(titleEl).toBeTruthy();
    expect(titleEl.textContent).toContain('Card Title');
  });

  it('should not display header when no title or subtitle', () => {
    fixture.componentRef.setInput('headerTitle', undefined);
    fixture.componentRef.setInput('subtitle', undefined);
    fixture.detectChanges();
    const headerEl = fixture.nativeElement.querySelector('.app-card__header');
    expect(headerEl).toBeNull();
  });
});
