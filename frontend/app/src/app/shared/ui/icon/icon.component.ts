import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IonIcon } from '@ionic/angular/standalone';

@Component({
  selector: 'app-icon',
  standalone: true,
  imports: [CommonModule, IonIcon],
  template: `
    <ion-icon
      [name]="name"
      [attr.aria-hidden]="ariaHidden"
      [style.font-size]="size ? size + 'px' : undefined"
      [class]="customClass"
    ></ion-icon>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        vertical-align: middle;
        line-height: 1;
      }
      ion-icon {
        font-size: inherit;
        color: inherit;
      }
    `,
  ],
})
export class IconComponent {
  @Input({ required: true }) name!: string;
  @Input() size?: number;
  @Input() ariaHidden = true;
  @Input() customClass = '';
}
