import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TopbarComponent } from './topbar.component';
import { AuthStore } from '../../core/auth/auth.store';
import { LayoutService } from '../layout.service';
import { NetworkStatusService } from '../../core/network/network-status.service';

describe('TopbarComponent', () => {
  let component: TopbarComponent;
  let fixture: ComponentFixture<TopbarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TopbarComponent],
      providers: [AuthStore, LayoutService, NetworkStatusService],
    }).compileComponents();

    fixture = TestBed.createComponent(TopbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and render notification bell button and menu toggle', () => {
    expect(component).toBeTruthy();
    const bellBtn = fixture.nativeElement.querySelector('.bell-btn');
    expect(bellBtn).toBeTruthy();

    const menuBtn = fixture.nativeElement.querySelector('.menu-btn');
    expect(menuBtn).toBeTruthy();
  });
});
