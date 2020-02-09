import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { JsonPipe } from "@angular/common";

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { SideNavComponent } from './side-nav/side-nav.component';
import { CipherStatsSummaryComponent } from './cipher-stats-summary/cipher-stats-summary.component';
import { CiphertextComponent } from './ciphertext/ciphertext.component';
import { PlaintextComponent } from './plaintext/plaintext.component';
import { BlockifyPipe } from './blockify.pipe';
import { DashboardComponent } from './dashboard/dashboard.component';
import { SettingsComponent } from './settings/settings.component';
import { NotFoundComponent } from './not-found/not-found.component';
import { SortablejsModule } from "ngx-sortablejs";
import { TransformersComponent } from './transformers/transformers.component';
import { BrowserAnimationsModule } from "@angular/platform-browser/animations";
import { MatTooltipModule } from '@angular/material';
import { DefaultHttpInterceptor } from "./interceptors/default-http-interceptor";
import { NewCipherModalComponent } from './new-cipher-modal/new-cipher-modal.component';
import { MatDialogModule } from "@angular/material/dialog";
import { MatButtonModule } from "@angular/material/button";
import { MatStepperModule } from "@angular/material/stepper";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";

@NgModule({
  declarations: [
    AppComponent,
    SideNavComponent,
    CipherStatsSummaryComponent,
    CiphertextComponent,
    PlaintextComponent,
    BlockifyPipe,
    DashboardComponent,
    SettingsComponent,
    NotFoundComponent,
    TransformersComponent,
    NewCipherModalComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    FormsModule,
    ReactiveFormsModule,
    SortablejsModule.forRoot({ animation: 150 }),
    BrowserAnimationsModule,
    MatTooltipModule,
    MatDialogModule,
    MatButtonModule,
    MatStepperModule,
    MatFormFieldModule,
    MatInputModule
  ],
  providers: [
    JsonPipe,
    {
      provide: HTTP_INTERCEPTORS,
      useClass: DefaultHttpInterceptor,
      multi: true
    },
  ],
  bootstrap: [AppComponent],
  entryComponents: [
    NewCipherModalComponent
  ]
})
export class AppModule { }
