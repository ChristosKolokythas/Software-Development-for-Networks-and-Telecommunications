
(function(l, r) { if (!l || l.getElementById('livereloadscript')) return; r = l.createElement('script'); r.async = 1; r.src = '//' + (self.location.host || 'localhost').split(':')[0] + ':35729/livereload.js?snipver=1'; r.id = 'livereloadscript'; l.getElementsByTagName('head')[0].appendChild(r) })(self.document);
var app = (function () {
    'use strict';

    function noop() { }
    function add_location(element, file, line, column, char) {
        element.__svelte_meta = {
            loc: { file, line, column, char }
        };
    }
    function run(fn) {
        return fn();
    }
    function blank_object() {
        return Object.create(null);
    }
    function run_all(fns) {
        fns.forEach(run);
    }
    function is_function(thing) {
        return typeof thing === 'function';
    }
    function safe_not_equal(a, b) {
        return a != a ? b == b : a !== b || ((a && typeof a === 'object') || typeof a === 'function');
    }
    function is_empty(obj) {
        return Object.keys(obj).length === 0;
    }
    function append(target, node) {
        target.appendChild(node);
    }
    function insert(target, node, anchor) {
        target.insertBefore(node, anchor || null);
    }
    function detach(node) {
        if (node.parentNode) {
            node.parentNode.removeChild(node);
        }
    }
    function element(name) {
        return document.createElement(name);
    }
    function attr(node, attribute, value) {
        if (value == null)
            node.removeAttribute(attribute);
        else if (node.getAttribute(attribute) !== value)
            node.setAttribute(attribute, value);
    }
    function children(element) {
        return Array.from(element.childNodes);
    }
    function set_style(node, key, value, important) {
        if (value === null) {
            node.style.removeProperty(key);
        }
        else {
            node.style.setProperty(key, value, important ? 'important' : '');
        }
    }
    function custom_event(type, detail, { bubbles = false, cancelable = false } = {}) {
        const e = document.createEvent('CustomEvent');
        e.initCustomEvent(type, bubbles, cancelable, detail);
        return e;
    }

    let current_component;
    function set_current_component(component) {
        current_component = component;
    }
    function get_current_component() {
        if (!current_component)
            throw new Error('Function called outside component initialization');
        return current_component;
    }
    /**
     * The `onMount` function schedules a callback to run as soon as the component has been mounted to the DOM.
     * It must be called during the component's initialisation (but doesn't need to live *inside* the component;
     * it can be called from an external module).
     *
     * `onMount` does not run inside a [server-side component](/docs#run-time-server-side-component-api).
     *
     * https://svelte.dev/docs#run-time-svelte-onmount
     */
    function onMount(fn) {
        get_current_component().$$.on_mount.push(fn);
    }

    const dirty_components = [];
    const binding_callbacks = [];
    const render_callbacks = [];
    const flush_callbacks = [];
    const resolved_promise = Promise.resolve();
    let update_scheduled = false;
    function schedule_update() {
        if (!update_scheduled) {
            update_scheduled = true;
            resolved_promise.then(flush);
        }
    }
    function add_render_callback(fn) {
        render_callbacks.push(fn);
    }
    // flush() calls callbacks in this order:
    // 1. All beforeUpdate callbacks, in order: parents before children
    // 2. All bind:this callbacks, in reverse order: children before parents.
    // 3. All afterUpdate callbacks, in order: parents before children. EXCEPT
    //    for afterUpdates called during the initial onMount, which are called in
    //    reverse order: children before parents.
    // Since callbacks might update component values, which could trigger another
    // call to flush(), the following steps guard against this:
    // 1. During beforeUpdate, any updated components will be added to the
    //    dirty_components array and will cause a reentrant call to flush(). Because
    //    the flush index is kept outside the function, the reentrant call will pick
    //    up where the earlier call left off and go through all dirty components. The
    //    current_component value is saved and restored so that the reentrant call will
    //    not interfere with the "parent" flush() call.
    // 2. bind:this callbacks cannot trigger new flush() calls.
    // 3. During afterUpdate, any updated components will NOT have their afterUpdate
    //    callback called a second time; the seen_callbacks set, outside the flush()
    //    function, guarantees this behavior.
    const seen_callbacks = new Set();
    let flushidx = 0; // Do *not* move this inside the flush() function
    function flush() {
        // Do not reenter flush while dirty components are updated, as this can
        // result in an infinite loop. Instead, let the inner flush handle it.
        // Reentrancy is ok afterwards for bindings etc.
        if (flushidx !== 0) {
            return;
        }
        const saved_component = current_component;
        do {
            // first, call beforeUpdate functions
            // and update components
            try {
                while (flushidx < dirty_components.length) {
                    const component = dirty_components[flushidx];
                    flushidx++;
                    set_current_component(component);
                    update(component.$$);
                }
            }
            catch (e) {
                // reset dirty state to not end up in a deadlocked state and then rethrow
                dirty_components.length = 0;
                flushidx = 0;
                throw e;
            }
            set_current_component(null);
            dirty_components.length = 0;
            flushidx = 0;
            while (binding_callbacks.length)
                binding_callbacks.pop()();
            // then, once components are updated, call
            // afterUpdate functions. This may cause
            // subsequent updates...
            for (let i = 0; i < render_callbacks.length; i += 1) {
                const callback = render_callbacks[i];
                if (!seen_callbacks.has(callback)) {
                    // ...so guard against infinite loops
                    seen_callbacks.add(callback);
                    callback();
                }
            }
            render_callbacks.length = 0;
        } while (dirty_components.length);
        while (flush_callbacks.length) {
            flush_callbacks.pop()();
        }
        update_scheduled = false;
        seen_callbacks.clear();
        set_current_component(saved_component);
    }
    function update($$) {
        if ($$.fragment !== null) {
            $$.update();
            run_all($$.before_update);
            const dirty = $$.dirty;
            $$.dirty = [-1];
            $$.fragment && $$.fragment.p($$.ctx, dirty);
            $$.after_update.forEach(add_render_callback);
        }
    }
    const outroing = new Set();
    function transition_in(block, local) {
        if (block && block.i) {
            outroing.delete(block);
            block.i(local);
        }
    }

    const globals = (typeof window !== 'undefined'
        ? window
        : typeof globalThis !== 'undefined'
            ? globalThis
            : global);
    function mount_component(component, target, anchor, customElement) {
        const { fragment, after_update } = component.$$;
        fragment && fragment.m(target, anchor);
        if (!customElement) {
            // onMount happens before the initial afterUpdate
            add_render_callback(() => {
                const new_on_destroy = component.$$.on_mount.map(run).filter(is_function);
                // if the component was destroyed immediately
                // it will update the `$$.on_destroy` reference to `null`.
                // the destructured on_destroy may still reference to the old array
                if (component.$$.on_destroy) {
                    component.$$.on_destroy.push(...new_on_destroy);
                }
                else {
                    // Edge case - component was destroyed immediately,
                    // most likely as a result of a binding initialising
                    run_all(new_on_destroy);
                }
                component.$$.on_mount = [];
            });
        }
        after_update.forEach(add_render_callback);
    }
    function destroy_component(component, detaching) {
        const $$ = component.$$;
        if ($$.fragment !== null) {
            run_all($$.on_destroy);
            $$.fragment && $$.fragment.d(detaching);
            // TODO null out other refs, including component.$$ (but need to
            // preserve final state?)
            $$.on_destroy = $$.fragment = null;
            $$.ctx = [];
        }
    }
    function make_dirty(component, i) {
        if (component.$$.dirty[0] === -1) {
            dirty_components.push(component);
            schedule_update();
            component.$$.dirty.fill(0);
        }
        component.$$.dirty[(i / 31) | 0] |= (1 << (i % 31));
    }
    function init(component, options, instance, create_fragment, not_equal, props, append_styles, dirty = [-1]) {
        const parent_component = current_component;
        set_current_component(component);
        const $$ = component.$$ = {
            fragment: null,
            ctx: [],
            // state
            props,
            update: noop,
            not_equal,
            bound: blank_object(),
            // lifecycle
            on_mount: [],
            on_destroy: [],
            on_disconnect: [],
            before_update: [],
            after_update: [],
            context: new Map(options.context || (parent_component ? parent_component.$$.context : [])),
            // everything else
            callbacks: blank_object(),
            dirty,
            skip_bound: false,
            root: options.target || parent_component.$$.root
        };
        append_styles && append_styles($$.root);
        let ready = false;
        $$.ctx = instance
            ? instance(component, options.props || {}, (i, ret, ...rest) => {
                const value = rest.length ? rest[0] : ret;
                if ($$.ctx && not_equal($$.ctx[i], $$.ctx[i] = value)) {
                    if (!$$.skip_bound && $$.bound[i])
                        $$.bound[i](value);
                    if (ready)
                        make_dirty(component, i);
                }
                return ret;
            })
            : [];
        $$.update();
        ready = true;
        run_all($$.before_update);
        // `false` as a special case of no DOM component
        $$.fragment = create_fragment ? create_fragment($$.ctx) : false;
        if (options.target) {
            if (options.hydrate) {
                const nodes = children(options.target);
                // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                $$.fragment && $$.fragment.l(nodes);
                nodes.forEach(detach);
            }
            else {
                // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                $$.fragment && $$.fragment.c();
            }
            if (options.intro)
                transition_in(component.$$.fragment);
            mount_component(component, options.target, options.anchor, options.customElement);
            flush();
        }
        set_current_component(parent_component);
    }
    /**
     * Base class for Svelte components. Used when dev=false.
     */
    class SvelteComponent {
        $destroy() {
            destroy_component(this, 1);
            this.$destroy = noop;
        }
        $on(type, callback) {
            if (!is_function(callback)) {
                return noop;
            }
            const callbacks = (this.$$.callbacks[type] || (this.$$.callbacks[type] = []));
            callbacks.push(callback);
            return () => {
                const index = callbacks.indexOf(callback);
                if (index !== -1)
                    callbacks.splice(index, 1);
            };
        }
        $set($$props) {
            if (this.$$set && !is_empty($$props)) {
                this.$$.skip_bound = true;
                this.$$set($$props);
                this.$$.skip_bound = false;
            }
        }
    }

    function dispatch_dev(type, detail) {
        document.dispatchEvent(custom_event(type, Object.assign({ version: '3.55.1' }, detail), { bubbles: true }));
    }
    function append_dev(target, node) {
        dispatch_dev('SvelteDOMInsert', { target, node });
        append(target, node);
    }
    function insert_dev(target, node, anchor) {
        dispatch_dev('SvelteDOMInsert', { target, node, anchor });
        insert(target, node, anchor);
    }
    function detach_dev(node) {
        dispatch_dev('SvelteDOMRemove', { node });
        detach(node);
    }
    function attr_dev(node, attribute, value) {
        attr(node, attribute, value);
        if (value == null)
            dispatch_dev('SvelteDOMRemoveAttribute', { node, attribute });
        else
            dispatch_dev('SvelteDOMSetAttribute', { node, attribute, value });
    }
    function validate_slots(name, slot, keys) {
        for (const slot_key of Object.keys(slot)) {
            if (!~keys.indexOf(slot_key)) {
                console.warn(`<${name}> received an unexpected slot "${slot_key}".`);
            }
        }
    }
    /**
     * Base class for Svelte components with some minor dev-enhancements. Used when dev=true.
     */
    class SvelteComponentDev extends SvelteComponent {
        constructor(options) {
            if (!options || (!options.target && !options.$$inline)) {
                throw new Error("'target' is a required option");
            }
            super();
        }
        $destroy() {
            super.$destroy();
            this.$destroy = () => {
                console.warn('Component was already destroyed'); // eslint-disable-line no-console
            };
        }
        $capture_state() { }
        $inject_state() { }
    }

    /* src\App.svelte generated by Svelte v3.55.1 */

    const { console: console_1 } = globals;
    const file = "src\\App.svelte";

    function create_fragment(ctx) {
    	let main;
    	let div;

    	const block = {
    		c: function create() {
    			main = element("main");
    			div = element("div");
    			attr_dev(div, "id", "map");
    			set_style(div, "width", "100%");
    			set_style(div, "height", "1000px");
    			add_location(div, file, 247, 1, 6672);
    			add_location(main, file, 246, 0, 6664);
    		},
    		l: function claim(nodes) {
    			throw new Error("options.hydrate only works if the component was compiled with the `hydratable: true` option");
    		},
    		m: function mount(target, anchor) {
    			insert_dev(target, main, anchor);
    			append_dev(main, div);
    		},
    		p: noop,
    		i: noop,
    		o: noop,
    		d: function destroy(detaching) {
    			if (detaching) detach_dev(main);
    		}
    	};

    	dispatch_dev("SvelteRegisterBlock", {
    		block,
    		id: create_fragment.name,
    		type: "component",
    		source: "",
    		ctx
    	});

    	return block;
    }

    function calculateRisk(gas, smoke, temp, uv) {
    	let gas_flag = gas > 1.0065;
    	let smoke_flag = smoke > 0.0915;
    	let temp_flag = temp > 50;
    	let rad_flag = uv > 6;

    	if (gas_flag && smoke_flag) ; else if (!gas_flag && !smoke_flag && (temp_flag && rad_flag)) {
    		return 2;
    	} else if (gas_flag) {
    		return 3;
    	} else if (gas_flag && smoke_flag && temp_flag && rad_flag) {
    		return 3;
    	} else {
    		return 1;
    	}
    }

    //Choose the iot device icon based on the danger level
    function chooseIcon(risk) {
    	let icon;

    	if (risk == 2) {
    		icon = 'yellow-warning.png';
    	} else if (risk == 3) {
    		icon = 'red-warning.png';
    	} else if (risk == 1) {
    		icon = 'iot-marker.png';
    	}

    	return icon;
    }

    //Set the circle color based on if the iot device is off
    function getCircleColor(gas, smoke, temp, uv) {
    	let circleColor;

    	if (gas == null && smoke == null && temp == null && uv == null) {
    		circleColor = '#ff0000';
    	} else {
    		circleColor = '#66ff33';
    	}

    	return circleColor;
    }

    function instance($$self, $$props, $$invalidate) {
    	let { $$slots: slots = {}, $$scope } = $$props;
    	validate_slots('App', slots, []);
    	let map;
    	let iotmarkers = {};
    	let androidmarkers = [];

    	onMount(async () => {
    		//Draw the map
    		const googleMapsScript = document.createElement('script');

    		googleMapsScript.src = `https://maps.googleapis.com/maps/api/js?key=AIzaSyCXPOGVmiQi21YXGnUmIDegztJeUfykaCc&callback=Function.prototype`;
    		document.head.appendChild(googleMapsScript);

    		googleMapsScript.onload = () => {
    			map = new google.maps.Map(document.getElementById('map'),
    			{
    					center: {
    						lat: 37.96822558982665,
    						lng: 23.76653761984567
    					},
    					zoom: 19
    				});

    			//Get the data from the api every second
    			setInterval(fetchData, 1000);
    		};
    	});

    	async function fetchData() {
    		try {
    			const response = await fetch('http://localhost:3000/api/devices');
    			const data = await response.json();

    			//Parse the Json
    			const lat = parseFloat(data.Lat);

    			const lng = parseFloat(data.Long);
    			const battery = data.data && parseInt(data.Battery);
    			const id = parseInt(data.DeviceID);
    			let smoke = null;
    			let gas = null;
    			let temp = null;
    			let uv = null;

    			if (data.data) {
    				for (const sensor of data.data) {
    					switch (sensor['Sensor Type']) {
    						case 'Smoke Sensor':
    							smoke = sensor['Sensor Value'];
    							break;
    						case 'Gas Sensor':
    							gas = sensor['Sensor Value'];
    							break;
    						case 'Thermal Sensor':
    							temp = sensor['Sensor Value'];
    							break;
    						case 'UV Sensor':
    							uv = sensor['Sensor Value'];
    							break;
    					}
    				}
    			}

    			if (id == 3) {
    				addAndroidMarker(lat, lng, id);
    			} else {
    				addIotMarkers(lat, lng, id, battery, gas, smoke, temp, uv);
    			}
    		} catch(error) {
    			console.error(error);
    		}
    	}

    	function addAndroidMarker(lat, lng, id) {
    		const oldMarker = androidmarkers[0];

    		if (oldMarker) {
    			//update the marker (position and infowindow) if it already exist
    			const marker = oldMarker.marker;

    			marker.setPosition({ lat, lng });
    			const infowindow1 = oldMarker.infowindow;

    			infowindow1.setContent(`
          <div>
            <p>Android Device: ${id}</p>
            <p>Position: (${lat}, ${lng})</p>
          </div>
        `);

    			return marker;
    		} else {
    			const marker = new google.maps.Marker({
    					position: { lat, lng },
    					map,
    					icon: 'android.png'
    				});

    			const infowindow = new google.maps.InfoWindow({
    					content: `
          <div>
            <p>Android Device: ${id}</p>
            <p>Position: (${lat}, ${lng})</p>
          </div>
        `
    				});

    			marker.addListener('click', () => {
    				infowindow.open(map, marker);
    			});

    			androidmarkers[0] = { marker, infowindow };
    			return marker;
    		}
    	}

    	function addIotMarkers(lat, lng, id, battery, gas, smoke, temp, uv) {
    		const oldMarker = iotmarkers[id];

    		if (oldMarker) {
    			//update iot marker infowindow, icon, circle (and position if needed)
    			oldMarker.marker.setPosition({ lat, lng });

    			oldMarker.infowindow.setContent(`
      <div>
        <p>Iot Device: ${id}</p>
        <p>Position: (${lat}, ${lng})</p> 
        <p>Battery: ${battery}%</p>          
        <p>Gas: ${gas}</p>
        <p>Smoke: ${smoke}</p>
        <p>Temperature: ${temp}</p>
        <p>Radiation: ${uv}</p>
      </div>
    `);

    			let risklevel = calculateRisk(gas, smoke, temp, uv);
    			oldMarker.marker.setIcon(chooseIcon(risklevel));

    			if (risklevel === 1) {
    				let circleColor = getCircleColor(gas, smoke, temp, uv);

    				if (oldMarker.circle != null) {
    					//if risk is low and the iot device has a circle under it update the circle
    					oldMarker.circle.setOptions({
    						strokeColor: circleColor,
    						fillColor: circleColor,
    						center: { lat, lng },
    						visible: true
    					});
    				} else {
    					//if the existing iot device doesn't  have a circle create a new one
    					let circle = new google.maps.Circle({
    							map,
    							radius: 4,
    							strokeColor: circleColor,
    							strokeOpacity: 0.8,
    							strokeWeight: 2,
    							fillColor: circleColor,
    							fillOpacity: 0.35,
    							center: { lat, lng },
    							visible: true
    						});

    					oldMarker.circle = circle;
    				}
    			} else {
    				if (oldMarker.circle) {
    					oldMarker.circle.setVisible(false);
    				}
    			}
    		} else {
    			//if a marker with the same id doesn't exist create a new one
    			let risklevel1 = calculateRisk(gas, smoke, temp, uv);

    			let marker = new google.maps.Marker({
    					position: { lat, lng },
    					map,
    					icon: chooseIcon(risklevel1)
    				});

    			let infowindow = new google.maps.InfoWindow({
    					content: `
        <div>
          <p>Iot Device: ${id}</p>
          <p>Position: (${lat}, ${lng})</p> 
          <p>Battery: ${battery}%</p>          
          <p>Gas: ${gas}</p>
          <p>Smoke: ${smoke}</p>
          <p>Temperature: ${temp}</p>
          <p>Radiation: ${uv}</p>
        </div>
      `
    				});

    			let circle;
    			let risklevel = calculateRisk(gas, smoke, temp, uv);

    			if (risklevel === 1) {
    				//if the risk is low draw a circle under the iot device
    				let circleColor = getCircleColor(gas, smoke, temp, uv);

    				circle = new google.maps.Circle({
    						map,
    						radius: 4,
    						strokeColor: circleColor,
    						strokeOpacity: 0.8,
    						strokeWeight: 2,
    						fillColor: circleColor,
    						fillOpacity: 0.35,
    						center: { lat, lng },
    						visible: true
    					});
    			}

    			iotmarkers[id] = { marker, infowindow, circle };

    			marker.addListener('click', () => {
    				infowindow.open(map, marker);
    			});

    			return marker;
    		}
    	}

    	const writable_props = [];

    	Object.keys($$props).forEach(key => {
    		if (!~writable_props.indexOf(key) && key.slice(0, 2) !== '$$' && key !== 'slot') console_1.warn(`<App> was created with unknown prop '${key}'`);
    	});

    	$$self.$capture_state = () => ({
    		onMount,
    		map,
    		iotmarkers,
    		androidmarkers,
    		fetchData,
    		addAndroidMarker,
    		addIotMarkers,
    		calculateRisk,
    		chooseIcon,
    		getCircleColor
    	});

    	$$self.$inject_state = $$props => {
    		if ('map' in $$props) map = $$props.map;
    		if ('iotmarkers' in $$props) iotmarkers = $$props.iotmarkers;
    		if ('androidmarkers' in $$props) androidmarkers = $$props.androidmarkers;
    	};

    	if ($$props && "$$inject" in $$props) {
    		$$self.$inject_state($$props.$$inject);
    	}

    	return [];
    }

    class App extends SvelteComponentDev {
    	constructor(options) {
    		super(options);
    		init(this, options, instance, create_fragment, safe_not_equal, {});

    		dispatch_dev("SvelteRegisterComponent", {
    			component: this,
    			tagName: "App",
    			options,
    			id: create_fragment.name
    		});
    	}
    }

    const app = new App({
    	target: document.body,

    });

    return app;

})();
//# sourceMappingURL=bundle.js.map
