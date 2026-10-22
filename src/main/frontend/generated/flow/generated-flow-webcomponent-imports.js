import { injectGlobalWebcomponentCss } from 'Frontend/generated/jar-resources/theme-util.js';

import { injectGlobalCss } from 'Frontend/generated/jar-resources/theme-util.js';

import { css, unsafeCSS, registerStyles } from '@vaadin/vaadin-themable-mixin';
import $cssFromFile_0 from '@vaadin/vaadin-lumo-styles/lumo.css?inline';
import '@vaadin/vertical-layout/src/vaadin-vertical-layout.js';
import '@vaadin/text-field/src/vaadin-text-field.js';
import '@vaadin/tooltip/src/vaadin-tooltip.js';
import '@vaadin/password-field/src/vaadin-password-field.js';
import '@vaadin/button/src/vaadin-button.js';
import 'Frontend/generated/jar-resources/disableOnClickFunctions.js';
import '@vaadin/notification/src/vaadin-notification.js';
import 'Frontend/generated/jar-resources/flow-component-renderer.js';
import 'Frontend/generated/jar-resources/flow-component-directive.js';
import 'lit';
import '@vaadin/common-frontend/ConnectionIndicator.js';
import 'Frontend/generated/jar-resources/ReactRouterOutletElement.tsx';
import 'react-router';
import 'react';

injectGlobalWebcomponentCss($cssFromFile_0.toString());
const loadOnDemand = (key) => {
  const pending = [];
  if (key === '3fdfcc07f8cbbdbb67f3b4bb7a87c36d7654c8bd8fe3eeae4c0af5d1568671a4') {
    pending.push(import('./chunks/chunk-1970f2b43d214a474152cc620f9e3f70f8b55799ae1b90a4db21e9b2f000f706.js'));
  }
  if (key === '07e4debbdfee40491d0c7db5df031e7b20eb73a026a64f09a0f62e3a0e218c53') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === 'a74b1fddf30c63c949540e7224de54046a64e070d330462c6246b2c11e5d1e16') {
    pending.push(import('./chunks/chunk-841cd24a32d838a91b92031cc045cf086a89cf2ee734ad33f873a33a277ed3ae.js'));
  }
  if (key === '39270ba84e1b681ace63edfe59aa9628268058c151dfea6a7b399b928d4099e2') {
    pending.push(import('./chunks/chunk-3a47e66d3087137d1af4e9ace8f4ec195adb33aa11b4f6d51951192e2ade4487.js'));
  }
  if (key === 'a08a25be0826dc88414702649ff11b0b35c30b5e757d2fb45ae12894a9f17599') {
    pending.push(import('./chunks/chunk-7060bcf4f0df44da8fc27403d6398bb7ac12fab379dbdb16bfde89008f7d66f1.js'));
  }
  if (key === '73efbf23f8f41e3e5e9a64d6c660187c1662548d416224d96b301f864c5e5606') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === '061641d98f7f9e4b531b3397850028d2b3c42326783223d1d013eb2302d16d5a') {
    pending.push(import('./chunks/chunk-ca68e3e8d27876d49abffdb87c2043792b2995b942989be158241a0aeb5feb4c.js'));
  }
  if (key === '0897dc979fad01349d8490a0e6bd37dfd6ba9ebc4b979cdc7baba506a7137d8e') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === 'fcfabfa3c704d73473a18cb8d2dcd3b4dd36bf2b75c950ae06fd815d844f2317') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === '1ccc510d675ef2b1301bedb8ceb65fd14932dd56d306c91abde034bbaa27948a') {
    pending.push(import('./chunks/chunk-7060bcf4f0df44da8fc27403d6398bb7ac12fab379dbdb16bfde89008f7d66f1.js'));
  }
  if (key === '6c8bbfa694b15ed5f960655208635766e8e6cc44bf0864d9d08e9708b001bb72') {
    pending.push(import('./chunks/chunk-1970f2b43d214a474152cc620f9e3f70f8b55799ae1b90a4db21e9b2f000f706.js'));
  }
  if (key === 'b371d341d89f5c2300a92130d1d98fc0a1e13296ef9d99bb7264f3a53b9b0970') {
    pending.push(import('./chunks/chunk-a135c493697421d1256a635f83a3a0cd483819ee908ddb9a3a9cc8e04379870b.js'));
  }
  if (key === 'cace40c20a669223ae7afdb0e4a528a377c9543d944bd0c01b453e17586f761b') {
    pending.push(import('./chunks/chunk-be621f203a1243ac70c3741aa3c8c13427ca88b80622fc889abe2d047b535e83.js'));
  }
  if (key === '1683d1b2b2f5114b610fa23ae90cec1e6b9d5562e4f1e371d92990c1cd4ea379') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === '1868cf0aa384b543a89a0af5ef70b8a500c3bc8a286257add8a7e50c52820409') {
    pending.push(import('./chunks/chunk-851224adee4337d8e9a1c2993c35894646238907ebc3a06c1345f5366e5933b7.js'));
  }
  if (key === '99112d78545b932237eeba466b71479d2a829537c05b3c53b58dc9dcc95c59b7') {
    pending.push(import('./chunks/chunk-ee33e14b7dc31e1d5df3253bb0d2b48807c3d5d87c2f3a56f898501544029736.js'));
  }
  if (key === '669d24f7d5a78e0644037da1950c180efb70b1030b8c036d4bdbe77daa8b6457') {
    pending.push(import('./chunks/chunk-5cfea213aa66cc2e3f7f18fc82f16881df3f795e495f755619e507bf92d5dccc.js'));
  }
  if (key === '13fe0f22937e9d19576d91fc61682ec472c91fde02be38221eac7d3f4347dd01') {
    pending.push(import('./chunks/chunk-58da7dd319ecc113813d4b00a62b5ffd9d5bfd16468608967d0774c41cff6229.js'));
  }
  if (key === '78d48a9bae2afc445d84cb00b83a355852f4b657a26c9f8329334993899969b8') {
    pending.push(import('./chunks/chunk-841cd24a32d838a91b92031cc045cf086a89cf2ee734ad33f873a33a277ed3ae.js'));
  }
  if (key === 'd97fc04845463bcf1a9c4bf6798c0a104a76798a3531d1fefa482d0d0f6724db') {
    pending.push(import('./chunks/chunk-4225c66405df0b6b7219b928795963be8d033043795a2a6984cd72009ae14ce0.js'));
  }
  if (key === '4579cfb8fa82f827059ed842810ac7a328eb08323ba64c70a4973c442e24933b') {
    pending.push(import('./chunks/chunk-1970f2b43d214a474152cc620f9e3f70f8b55799ae1b90a4db21e9b2f000f706.js'));
  }
  if (key === 'd2e9fc0bcabe05e6f116a583d9fb066bed17b1dcafdd916a001c687609e30f6e') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === 'de991a2a8c95ee6f1ddf11e59d994af2f779ead837fb9e5a9bb07dcf8d879f16') {
    pending.push(import('./chunks/chunk-0dcdef5c3b9d4bad36e39eca0547bc43c34315618b15ef266152a4231f7b9502.js'));
  }
  if (key === 'febc42229954bc45ae16786d4e24caf1b0d826ce9f4c29bf0fa092515b97ee4f') {
    pending.push(import('./chunks/chunk-7243307d3cf692856ee355c606748420a9ec62a98e6a2c74d80f28025f7d0981.js'));
  }
  if (key === 'c95bc6f39438d46784ddfb8171c7d1f4c70c4e217ecf799b301ab71bfb682648') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === '70e1a11287cf5327f17a55e80431449e634fd0628714390ee2920eff3fa70487') {
    pending.push(import('./chunks/chunk-15410e7c23c882b965f689ac5cbd61fb9ba403a68a6e8bc95e4b4c515f0b370e.js'));
  }
  if (key === 'aaa8600fcf218f704560d2edb1888177c81d42001aac93c7e0d9d3e201a24fe1') {
    pending.push(import('./chunks/chunk-15410e7c23c882b965f689ac5cbd61fb9ba403a68a6e8bc95e4b4c515f0b370e.js'));
  }
  if (key === '289263a50ef253d3c6d45a61d12cb23fa77a2ef72b8feb7e11ccd9474a4fab0d') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === '65f6905f959f305e1ae1181975ffecd5e2bc240f49525cd4260b0243f29ca710') {
    pending.push(import('./chunks/chunk-9de4b1d3390d7fcabc1ca813b24df3a5a45575bb0f9b0e4d4d9db4e1ab85537b.js'));
  }
  if (key === '1ae7ee724d7a9ac7008eadfa46e382d55af2068a482781d8ea16e67ba2b70c3b') {
    pending.push(import('./chunks/chunk-ec0012918b9f411cd45ebfd85644a70186889644e5d6feede583c8c46873fd88.js'));
  }
  if (key === 'ce78341ee5bea98a7c4c50ca12dda9d4961a61d597203890b7c04b9cd5c8d0e5') {
    pending.push(import('./chunks/chunk-3a47e66d3087137d1af4e9ace8f4ec195adb33aa11b4f6d51951192e2ade4487.js'));
  }
  if (key === '96fee0866a4cd4b39cc6715e9f5a65227fd16a2f2afd119125136cceea48469c') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === '930ad15343c529a3500bebdecb5dcec41f23cc2a1583e3e42520e51c2294907a') {
    pending.push(import('./chunks/chunk-4fb3891814a26fa63454b72380d724eb49a763a9df90221370b4dd6501deb2e4.js'));
  }
  if (key === '09f02238414b209f215851bf91c8eb4e0bf2893e259116b487d0c0cf0d05a3bb') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === '134e75534cb1480a5b0df4a13a4da430cc50819b6dd023b557e90d6d3691ba8f') {
    pending.push(import('./chunks/chunk-7060bcf4f0df44da8fc27403d6398bb7ac12fab379dbdb16bfde89008f7d66f1.js'));
  }
  if (key === '4b8871c3bddb532eb353b2e33342a94e1d6949568b79a6e8b30de4bb39b4ff3b') {
    pending.push(import('./chunks/chunk-daa3482c22d3d9f882c858e83b1a531c5534bf6b91cac9b4ade8884b87d4ec0f.js'));
  }
  if (key === 'b503dbb0feaa32a918546cef506889ba9b65725210d04b7b6730b4f4251382c7') {
    pending.push(import('./chunks/chunk-851224adee4337d8e9a1c2993c35894646238907ebc3a06c1345f5366e5933b7.js'));
  }
  if (key === '2455c790e736772b8849684b7bb3827e755bdae32013497a4020c3dab48f022f') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === '4a0f7f0c21c287cf54417ef5a9d1d868990d8a0b98c5bcdf4a24bc765d144997') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === 'd90b7add71d8681e13d7d6a43aef86cd1aa22eed9a651d8300646b6e803c0343') {
    pending.push(import('./chunks/chunk-b385f77161c2d8c538c63c217b105b5f000c69d3580e6327aca2b0c523b1ec8e.js'));
  }
  if (key === '15663c5566da1a7956892e7bafcafa3a68e8bd99d2318a89a801660a1d7dce5e') {
    pending.push(import('./chunks/chunk-4c37978babf8a241de4c9671df3426043ca42ed603439ef77cd694f0cd4e3c57.js'));
  }
  if (key === '3263e4ad67ed2ccbc44ec8e8a0808d17aef305a35f1bcfb3acefb82b9dbc8422') {
    pending.push(import('./chunks/chunk-336a0732b86aacc1094bbb6facadb94135f8d50ba37960600a41484326423cd4.js'));
  }
  if (key === '350a434e9a9d36eb7c4e13509c172f2c7eec660c0db0c145cd1cad4dba3d3e68') {
    pending.push(import('./chunks/chunk-13d766eefab886158e8d0343c2aa284b3be16b696d74712f9a6e01af761dfd6f.js'));
  }
  return Promise.all(pending);
}
window.Vaadin = window.Vaadin || {};
window.Vaadin.Flow = window.Vaadin.Flow || {};
window.Vaadin.Flow.loadOnDemand = loadOnDemand;
window.Vaadin.Flow.resetFocus = () => {
 let ae=document.activeElement;
 while(ae&&ae.shadowRoot) ae = ae.shadowRoot.activeElement;
 return !ae || ae.blur() || ae.focus() || true;
}