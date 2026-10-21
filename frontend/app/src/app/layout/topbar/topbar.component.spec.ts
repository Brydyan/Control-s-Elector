import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { providePrimeNG } from 'primeng/config';
import { TopbarComponent } from './topbar.component';
import { AuthStore } from '../../core/auth/auth.store';
import { LayoutService } from '../layout.service';
import { NetworkStatusService } from '../../core/network/network-status.service';
import { ThemeService } from '../../../theme/theme.service';

describe('TopbarComponent', () => {
  let component: TopbarComponent;
  let fixture: ComponentFixture<TopbarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TopbarComponent, RouterTestingModule],
      providers: [
        AuthStore,
        LayoutService,
        NetworkStatusService,
        ThemeService,
        providePrimeNG(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TopbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and render notification bell button and menu toggle', () => {
    expect(component).toBeTruthy();

    const bellIcon = fixture.nativeElement.querySelector('.pi-bell');
    expect(bellIcon).toBeTruthy();

    const menuIcon = fixture.nativeElement.querySelector('.pi-bars');
    expect(menuIcon).toBeTruthy();
  });

  it('should render the route title and subtitle', () => {
    fixture.componentRef.setInput('title', 'Resumen General');
    fixture.componentRef.setInput('subtitle', 'Elecciones Seccionales 2027');
    fixture.detectChanges();

    const title = fixture.nativeElement.querySelector('.layout-topbar-title .title');
    expect(title.textContent).toContain('Resumen General');
  });

  it('should toggle the dark scheme through the theme service', () => {
    const theme = TestBed.inject(ThemeService);
    expect(theme.darkMode()).toBe(false);

    component.toggleDarkMode();

    expect(theme.darkMode()).toBe(true);
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);
  });
});
