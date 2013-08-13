import device;
import event.Emitter as Emitter;
import util.setProperty as setProperty;

var LocalNotify = Class(Emitter, function (supr) {
	var _activeCB = [];
	var _getCB = {};
	var _pending = [];
	var onNotify;

	var deliverPending = function() {
		if (onNotify) {
			for (var ii = 0; ii < _pending.length; ++ii) {
				onNotify(_pending[ii]);
			}
			_pending.length = 0;
		}
	}

	this.init = function() {
		supr(this, 'init', arguments);

		NATIVE.events.registerHandler("LocalNotifyPlugin", "List", function(evt) {
			for (var ii = 0; ii < _activeCB.length; ++ii) {
				_activeCB[ii](evt.list);
			}
			_activeCB.length = 0;
		});

		NATIVE.events.registerHandler("LocalNotifyPlugin", "Get", function(evt) {
			var cbs = _getCB[evt.tag];
			if (cbs) {
				for (var ii = 0; ii < cbs.length; ++ii) {
					cbs[ii](evt.info);
				}
				cbs.length = 0;
			}
		});

		NATIVE.events.registerHandler("LocalNotifyPlugin", "OnNotify", function(evt) {
			if (onNotify) {
				onNotify(evt);
			} else {
				_pending.push(evt);
			}
		});

		setProperty(this, "onNotify", {
			set: function(f) {
				// If a callback is being set,
				if (typeof f === "function") {
					onNotify = f;

					setTimeout(deliverPending, 0);
				} else {
					onNotify = null;
				}
			},
			get: function() {
				return onNotify;
			}
		});

		// Tell the plugin we are ready to handle events
		NATIVE.plugins.sendEvent("LocalNotifyPlugin", "Ready", "{}");
	};

	this.list = function(next) {
		if (_activeCB.length === 0) {
			NATIVE.plugins.sendEvent("LocalNotifyPlugin", "List", "{}");
		}
		_activeCB.push(next);
	}

	this.get = function(tag, next) {
		NATIVE.plugins.sendEvent("LocalNotifyPlugin", "Get", JSON.stringify({
			tag: tag
		}));

		if (_getCB[tag]) {
			_getCB[tag].push(next);
		} else {
			_getCB[tag] = [next];
		}
	}

	this.removeAll = function() {
		NATIVE.plugins.sendEvent("LocalNotifyPlugin", "RemoveAll", "{}");
	}

	this.remove = function(tag) {
		NATIVE.plugins.sendEvent("LocalNotifyPlugin", "Remove", JSON.stringify({
			tag: tag
		}));
	}

	this.add = function(opts) {
		NATIVE.plugins.sendEvent("LocalNotifyPlugin", "Add", JSON.stringify(opts));
	}
});

exports = new LocalNotify();

